#include "descriptor_parser.h"

#include "logging.h"

#include <algorithm>
#include <cstddef>

namespace {
constexpr uint8_t kDescriptorTypeInterface = 0x04;
constexpr uint8_t kDescriptorTypeEndpoint = 0x05;
constexpr uint8_t kDescriptorTypeCsInterface = 0x24;

constexpr uint8_t kUsbClassAudio = 0x01;
constexpr uint8_t kUsbAudioSubclassStreaming = 0x02;

constexpr uint8_t kCsSubtypeFormatType = 0x02;
constexpr uint8_t kFormatTypeI = 0x01;
constexpr uint8_t kEndpointTransferIsochronous = 0x01;
constexpr uint8_t kEndpointDirectionMask = 0x80;
}  // namespace

bool DescriptorParser::Parse(const std::vector<uint8_t>& raw_descriptors) {
    alt_settings_.clear();
    StreamingAltSetting* current_alt = nullptr;

    std::size_t offset = 0;
    while (offset + 2 <= raw_descriptors.size()) {
        const auto length = static_cast<std::size_t>(raw_descriptors[offset]);
        const auto type = raw_descriptors[offset + 1];
        if (length < 2 || offset + length > raw_descriptors.size()) {
            USB_POC_LOGW("Malformed USB descriptor at offset=%zu length=%zu", offset, length);
            return false;
        }

        const uint8_t* descriptor = raw_descriptors.data() + offset;
        switch (type) {
            case kDescriptorTypeInterface: {
                if (length >= 9) {
                    const auto interface_class = descriptor[5];
                    const auto interface_subclass = descriptor[6];
                    if (interface_class == kUsbClassAudio &&
                        interface_subclass == kUsbAudioSubclassStreaming) {
                        StreamingAltSetting alt_setting;
                        alt_setting.interface_number = descriptor[2];
                        alt_setting.alternate_setting = descriptor[3];
                        alt_settings_.push_back(alt_setting);
                        current_alt = &alt_settings_.back();
                    } else {
                        current_alt = nullptr;
                    }
                }
                break;
            }

            case kDescriptorTypeEndpoint: {
                if (current_alt != nullptr && length >= 7) {
                    const auto endpoint_address = descriptor[2];
                    const auto attributes = static_cast<uint8_t>(descriptor[3] & 0x03);
                    if ((endpoint_address & kEndpointDirectionMask) == 0 &&
                        attributes == kEndpointTransferIsochronous) {
                        current_alt->endpoint_address = endpoint_address;
                        current_alt->max_packet_size =
                            static_cast<uint16_t>(descriptor[4] | (descriptor[5] << 8));
                        current_alt->interval = descriptor[6];
                    }
                }
                break;
            }

            case kDescriptorTypeCsInterface: {
                if (current_alt != nullptr && length >= 8 && descriptor[2] == kCsSubtypeFormatType) {
                    const auto format_type = descriptor[3];
                    if (format_type == kFormatTypeI) {
                        current_alt->channels = descriptor[4];
                        current_alt->subframe_size = descriptor[5];
                        current_alt->bit_resolution = descriptor[6];
                        const auto sample_rate_count = descriptor[7];
                        if (sample_rate_count == 0) {
                            USB_POC_LOGW(
                                "Continuous sample-rate range not supported in Milestone 1 for interface=%d alt=%d",
                                current_alt->interface_number,
                                current_alt->alternate_setting);
                        } else {
                            const std::size_t expected = 8 + static_cast<std::size_t>(sample_rate_count) * 3U;
                            if (length >= expected) {
                                current_alt->sample_rates_hz.clear();
                                for (std::size_t index = 0; index < sample_rate_count; ++index) {
                                    const auto base = 8U + index * 3U;
                                    const auto sample_rate =
                                        descriptor[base] |
                                        (descriptor[base + 1] << 8) |
                                        (descriptor[base + 2] << 16);
                                    current_alt->sample_rates_hz.push_back(sample_rate);
                                }
                            }
                        }
                    }
                }
                break;
            }

            default:
                break;
        }

        offset += length;
    }

    USB_POC_LOGI("Parsed %zu streaming alternate settings", alt_settings_.size());
    return !alt_settings_.empty();
}

std::optional<StreamingAltSetting> DescriptorParser::FindExactMatch(
    int sample_rate_hz,
    int bit_depth,
    int channels) const {
    const auto bytes_per_frame = channels * ((bit_depth + 7) / 8);
    const auto required_packet_bytes =
        static_cast<int>((sample_rate_hz * bytes_per_frame + 999) / 1000);

    for (const auto& alt : alt_settings_) {
        if (alt.endpoint_address == 0) {
            continue;
        }
        if (alt.channels != channels || alt.bit_resolution != bit_depth) {
            continue;
        }
        if (std::find(alt.sample_rates_hz.begin(), alt.sample_rates_hz.end(), sample_rate_hz) ==
            alt.sample_rates_hz.end()) {
            continue;
        }
        if (alt.max_packet_size < required_packet_bytes) {
            USB_POC_LOGW(
                "Alt setting interface=%d alt=%d packet=%u too small for sr=%d bits=%d ch=%d",
                alt.interface_number,
                alt.alternate_setting,
                alt.max_packet_size,
                sample_rate_hz,
                bit_depth,
                channels);
            continue;
        }
        return alt;
    }
    return std::nullopt;
}
