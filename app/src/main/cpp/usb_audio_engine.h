#pragma once

#include "descriptor_parser.h"
#include "ring_buffer.h"

#include <linux/usb/ch9.h>
#include <linux/usbdevice_fs.h>

#include <atomic>
#include <cstdint>
#include <mutex>
#include <optional>
#include <thread>
#include <vector>

class UsbAudioEngine {
public:
    UsbAudioEngine(int device_fd, std::vector<uint8_t> raw_descriptors, int vendor_id, int product_id);
    ~UsbAudioEngine();

    bool Initialize();
    bool StartStream(int sample_rate_hz, int bit_depth, int channels);
    int WritePcm(const uint8_t* data, std::size_t size_bytes);
    void StopStream();
    void Release();

private:
    class UniqueFd {
    public:
        UniqueFd() = default;
        explicit UniqueFd(int fd) : fd_(fd) {}
        ~UniqueFd();

        UniqueFd(const UniqueFd&) = delete;
        UniqueFd& operator=(const UniqueFd&) = delete;

        UniqueFd(UniqueFd&& other) noexcept;
        UniqueFd& operator=(UniqueFd&& other) noexcept;

        int get() const { return fd_; }
        bool valid() const { return fd_ >= 0; }
        int release();
        void reset(int fd = -1);

    private:
        int fd_ = -1;
    };

    struct ActiveStream {
        StreamingAltSetting alt_setting;
        int sample_rate_hz = 0;
        int bit_depth = 0;
        int channels = 0;
    };

    bool ClaimAndActivateLocked(const StreamingAltSetting& alt_setting, int sample_rate_hz);
    bool ConfigureSampleRateLocked(const StreamingAltSetting& alt_setting, int sample_rate_hz);
    bool TransferLoop(ActiveStream active_stream);
    bool SubmitAndReapIsoUrb(
        const ActiveStream& active_stream,
        std::vector<uint8_t>& transfer_buffer,
        std::vector<uint8_t>& urb_storage);
    void SignalStopEvent();
    void DeactivateInterfaceLocked();

    UniqueFd device_fd_;
    UniqueFd stop_event_fd_;
    std::vector<uint8_t> raw_descriptors_;
    DescriptorParser descriptor_parser_;
    BoundedRingBuffer ring_buffer_;
    std::mutex state_mutex_;
    std::optional<ActiveStream> active_stream_;
    std::thread transfer_thread_;
    std::atomic<bool> running_{false};
    int vendor_id_ = 0;
    int product_id_ = 0;
    int claimed_interface_ = -1;
};
