#include "usb_audio_engine.h"

#include "logging.h"

#include <poll.h>
#include <sys/eventfd.h>
#include <sys/ioctl.h>
#include <unistd.h>

#include <cerrno>
#include <cstring>
#include <utility>

namespace {
constexpr std::size_t kRingBufferCapacityBytes = 256 * 1024;
constexpr std::size_t kIsoPacketsPerUrb = 8;
constexpr uint8_t kUacSetCur = 0x01;
constexpr uint8_t kUacEndpointSamplingFreqControl = 0x01;
constexpr int kControlTimeoutMs = 1000;
}  // namespace

UsbAudioEngine::UsbAudioEngine(
    int device_fd,
    std::vector<uint8_t> raw_descriptors,
    int vendor_id,
    int product_id)
    : device_fd_(device_fd),
      raw_descriptors_(std::move(raw_descriptors)),
      ring_buffer_(kRingBufferCapacityBytes),
      vendor_id_(vendor_id),
      product_id_(product_id) {
    USB_POC_LOGI("Allocating UsbAudioEngine vid=%d pid=%d", vendor_id_, product_id_);
}

UsbAudioEngine::~UsbAudioEngine() {
    Release();
    USB_POC_LOGI("Destroying UsbAudioEngine");
}

bool UsbAudioEngine::Initialize() {
    if (!device_fd_.valid()) {
        USB_POC_LOGE("Invalid device fd during initialization");
        return false;
    }
    const bool parsed = descriptor_parser_.Parse(raw_descriptors_);
    USB_POC_LOGI("Descriptor parse result=%d", parsed ? 1 : 0);
    return parsed;
}

bool UsbAudioEngine::StartStream(int sample_rate_hz, int bit_depth, int channels) {
    std::optional<ActiveStream> local_stream;
    {
        std::scoped_lock<std::mutex> lock(state_mutex_);
        if (running_) {
            USB_POC_LOGW("StartStream requested while stream is already running");
            return false;
        }
        const auto match = descriptor_parser_.FindExactMatch(sample_rate_hz, bit_depth, channels);
        if (!match.has_value()) {
            USB_POC_LOGE(
                "No exact USB alt setting for sr=%d bits=%d ch=%d",
                sample_rate_hz,
                bit_depth,
                channels);
            return false;
        }
        if (!ClaimAndActivateLocked(*match, sample_rate_hz)) {
            return false;
        }
        ActiveStream stream;
        stream.alt_setting = *match;
        stream.sample_rate_hz = sample_rate_hz;
        stream.bit_depth = bit_depth;
        stream.channels = channels;
        active_stream_ = stream;
        ring_buffer_.Reset();
        running_ = true;
        local_stream = active_stream_;
        stop_event_fd_.reset(eventfd(0, EFD_NONBLOCK | EFD_CLOEXEC));
        if (!stop_event_fd_.valid()) {
            USB_POC_LOGE("eventfd failed: %s", std::strerror(errno));
            running_ = false;
            DeactivateInterfaceLocked();
            active_stream_.reset();
            return false;
        }
        USB_POC_LOGI(
            "Starting USB stream sr=%d bits=%d ch=%d iface=%d alt=%d ep=0x%02x",
            sample_rate_hz,
            bit_depth,
            channels,
            match->interface_number,
            match->alternate_setting,
            match->endpoint_address);
    }

    transfer_thread_ = std::thread([this, local_stream]() {
        if (!local_stream.has_value()) {
            return;
        }
        const bool ok = TransferLoop(*local_stream);
        if (!ok) {
            USB_POC_LOGW("USB transfer loop exited with errors");
        }
    });
    return true;
}

int UsbAudioEngine::WritePcm(const uint8_t* data, std::size_t size_bytes) {
    if (!running_) {
        USB_POC_LOGW("WritePcm ignored because USB stream is not running");
        return -1;
    }
    return ring_buffer_.Write(data, size_bytes);
}

void UsbAudioEngine::StopStream() {
    std::thread thread_to_join;
    {
        std::scoped_lock<std::mutex> lock(state_mutex_);
        if (!running_) {
            return;
        }
        USB_POC_LOGI("Stopping USB stream");
        running_ = false;
        ring_buffer_.Stop();
        SignalStopEvent();
        thread_to_join = std::move(transfer_thread_);
    }

    if (thread_to_join.joinable()) {
        thread_to_join.join();
    }

    std::scoped_lock<std::mutex> lock(state_mutex_);
    DeactivateInterfaceLocked();
    active_stream_.reset();
    stop_event_fd_.reset();
}

void UsbAudioEngine::Release() {
    StopStream();
    std::scoped_lock<std::mutex> lock(state_mutex_);
    DeactivateInterfaceLocked();
    device_fd_.reset();
}

UsbAudioEngine::UniqueFd::~UniqueFd() {
    reset();
}

UsbAudioEngine::UniqueFd::UniqueFd(UniqueFd&& other) noexcept : fd_(other.release()) {}

UsbAudioEngine::UniqueFd& UsbAudioEngine::UniqueFd::operator=(UniqueFd&& other) noexcept {
    if (this != &other) {
        reset(other.release());
    }
    return *this;
}

int UsbAudioEngine::UniqueFd::release() {
    const int old = fd_;
    fd_ = -1;
    return old;
}

void UsbAudioEngine::UniqueFd::reset(int fd) {
    if (fd_ >= 0) {
        close(fd_);
    }
    fd_ = fd;
}

bool UsbAudioEngine::ClaimAndActivateLocked(
    const StreamingAltSetting& alt_setting,
    int sample_rate_hz) {
    int interface_number = alt_setting.interface_number;
    if (ioctl(device_fd_.get(), USBDEVFS_CLAIMINTERFACE, &interface_number) < 0 &&
        errno != EBUSY) {
        USB_POC_LOGE("USBDEVFS_CLAIMINTERFACE failed: %s", std::strerror(errno));
        return false;
    }
    claimed_interface_ = alt_setting.interface_number;

    usbdevfs_setinterface set_interface{};
    set_interface.interface = alt_setting.interface_number;
    set_interface.altsetting = alt_setting.alternate_setting;
    if (ioctl(device_fd_.get(), USBDEVFS_SETINTERFACE, &set_interface) < 0) {
        USB_POC_LOGE("USBDEVFS_SETINTERFACE failed: %s", std::strerror(errno));
        DeactivateInterfaceLocked();
        return false;
    }

    if (alt_setting.sample_rates_hz.size() > 1 &&
        !ConfigureSampleRateLocked(alt_setting, sample_rate_hz)) {
        USB_POC_LOGE("USB sample-rate negotiation failed");
        DeactivateInterfaceLocked();
        return false;
    }

    return true;
}

bool UsbAudioEngine::ConfigureSampleRateLocked(
    const StreamingAltSetting& alt_setting,
    int sample_rate_hz) {
    uint8_t payload[3] = {
        static_cast<uint8_t>(sample_rate_hz & 0xff),
        static_cast<uint8_t>((sample_rate_hz >> 8) & 0xff),
        static_cast<uint8_t>((sample_rate_hz >> 16) & 0xff),
    };

    usbdevfs_ctrltransfer transfer{};
    transfer.bRequestType = USB_DIR_OUT | USB_TYPE_CLASS | USB_RECIP_ENDPOINT;
    transfer.bRequest = kUacSetCur;
    transfer.wValue = static_cast<uint16_t>(kUacEndpointSamplingFreqControl << 8);
    transfer.wIndex = alt_setting.endpoint_address;
    transfer.wLength = sizeof(payload);
    transfer.timeout = kControlTimeoutMs;
    transfer.data = payload;

    const int result = ioctl(device_fd_.get(), USBDEVFS_CONTROL, &transfer);
    if (result < 0) {
        USB_POC_LOGW(
            "USB sample-rate control failed for ep=0x%02x: %s",
            alt_setting.endpoint_address,
            std::strerror(errno));
        return false;
    }
    USB_POC_LOGI("Negotiated USB sample rate=%d Hz", sample_rate_hz);
    return true;
}

bool UsbAudioEngine::TransferLoop(ActiveStream active_stream) {
    const auto bytes_per_frame =
        static_cast<std::size_t>(active_stream.channels * ((active_stream.bit_depth + 7) / 8));
    const auto payload_bytes =
        std::min<std::size_t>(
            active_stream.alt_setting.max_packet_size,
            (static_cast<std::size_t>(active_stream.sample_rate_hz) * bytes_per_frame + 999U) / 1000U);
    std::vector<uint8_t> transfer_buffer(kIsoPacketsPerUrb * payload_bytes, 0U);
    std::vector<uint8_t> urb_storage(
        sizeof(usbdevfs_urb) + sizeof(usbdevfs_iso_packet_desc) * kIsoPacketsPerUrb,
        0U);

    USB_POC_LOGI(
        "USB transfer loop started payload=%zu packets=%zu",
        payload_bytes,
        kIsoPacketsPerUrb);

    while (running_) {
        const auto bytes = ring_buffer_.ReadOrSilence(transfer_buffer.data(), transfer_buffer.size());
        if (bytes == 0U && !running_) {
            break;
        }
        if (!SubmitAndReapIsoUrb(active_stream, transfer_buffer, urb_storage)) {
            return false;
        }
    }

    USB_POC_LOGI("USB transfer loop stopped");
    return true;
}

bool UsbAudioEngine::SubmitAndReapIsoUrb(
    const ActiveStream& active_stream,
    std::vector<uint8_t>& transfer_buffer,
    std::vector<uint8_t>& urb_storage) {
    std::fill(urb_storage.begin(), urb_storage.end(), 0U);
    auto* urb = reinterpret_cast<usbdevfs_urb*>(urb_storage.data());
    urb->type = USBDEVFS_URB_TYPE_ISO;
    urb->endpoint = active_stream.alt_setting.endpoint_address;
    urb->buffer = transfer_buffer.data();
    urb->buffer_length = static_cast<int>(transfer_buffer.size());
    urb->number_of_packets = kIsoPacketsPerUrb;
    urb->flags = USBDEVFS_URB_ISO_ASAP;

    const auto packet_length = static_cast<unsigned int>(transfer_buffer.size() / kIsoPacketsPerUrb);
    for (std::size_t index = 0; index < kIsoPacketsPerUrb; ++index) {
        urb->iso_frame_desc[index].length = packet_length;
    }

    if (ioctl(device_fd_.get(), USBDEVFS_SUBMITURB, urb) < 0) {
        USB_POC_LOGE("USBDEVFS_SUBMITURB failed: %s", std::strerror(errno));
        return false;
    }

    while (running_) {
        pollfd fds[2] = {
            {},
            {},
        };
        fds[0].fd = device_fd_.get();
        fds[0].events = POLLIN | POLLOUT;
        fds[1].fd = stop_event_fd_.get();
        fds[1].events = POLLIN;

        const int poll_result = poll(fds, 2, -1);
        if (poll_result < 0) {
            if (errno == EINTR) {
                continue;
            }
            USB_POC_LOGE("poll failed: %s", std::strerror(errno));
            return false;
        }

        if ((fds[1].revents & POLLIN) != 0) {
            ioctl(device_fd_.get(), USBDEVFS_DISCARDURB, urb);
            return false;
        }

        void* completed = nullptr;
        const int reap_result = ioctl(device_fd_.get(), USBDEVFS_REAPURB, &completed);
        if (reap_result == 0) {
            auto* completed_urb = reinterpret_cast<usbdevfs_urb*>(completed);
            if (completed_urb != urb) {
                USB_POC_LOGW("Reaped unexpected URB instance");
            }
            if (urb->status != 0) {
                USB_POC_LOGE("Iso URB completed with status=%d", urb->status);
                return false;
            }
            return true;
        }
        if (errno != EAGAIN && errno != EINTR) {
            USB_POC_LOGE("USBDEVFS_REAPURB failed: %s", std::strerror(errno));
            return false;
        }
    }

    ioctl(device_fd_.get(), USBDEVFS_DISCARDURB, urb);
    return false;
}

void UsbAudioEngine::SignalStopEvent() {
    if (!stop_event_fd_.valid()) {
        return;
    }
    uint64_t value = 1;
    const ssize_t written = write(stop_event_fd_.get(), &value, sizeof(value));
    if (written < 0 && errno != EAGAIN) {
        USB_POC_LOGW("Failed to signal stop event: %s", std::strerror(errno));
    }
}

void UsbAudioEngine::DeactivateInterfaceLocked() {
    if (!device_fd_.valid() || claimed_interface_ < 0) {
        claimed_interface_ = -1;
        return;
    }

    usbdevfs_setinterface set_interface{};
    set_interface.interface = claimed_interface_;
    set_interface.altsetting = 0;
    ioctl(device_fd_.get(), USBDEVFS_SETINTERFACE, &set_interface);

    int interface_number = claimed_interface_;
    ioctl(device_fd_.get(), USBDEVFS_RELEASEINTERFACE, &interface_number);
    claimed_interface_ = -1;
}
