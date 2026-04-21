#pragma once

#include <cstdint>
#include <optional>
#include <vector>

struct StreamingAltSetting {
    int interface_number = -1;
    int alternate_setting = -1;
    uint8_t endpoint_address = 0;
    uint16_t max_packet_size = 0;
    uint8_t interval = 0;
    uint8_t channels = 0;
    uint8_t subframe_size = 0;
    uint8_t bit_resolution = 0;
    std::vector<int> sample_rates_hz;
};

class DescriptorParser {
public:
    bool Parse(const std::vector<uint8_t>& raw_descriptors);
    std::optional<StreamingAltSetting> FindExactMatch(
        int sample_rate_hz,
        int bit_depth,
        int channels) const;

private:
    std::vector<StreamingAltSetting> alt_settings_;
};
