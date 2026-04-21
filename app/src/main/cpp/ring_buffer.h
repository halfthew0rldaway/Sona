#pragma once

#include <algorithm>
#include <chrono>
#include <condition_variable>
#include <cstddef>
#include <cstdint>
#include <cstring>
#include <mutex>
#include <vector>

class BoundedRingBuffer {
public:
    explicit BoundedRingBuffer(std::size_t capacity_bytes)
        : buffer_(capacity_bytes, 0) {}

    int Write(const uint8_t* data, std::size_t size_bytes) {
        std::size_t written = 0;
        std::unique_lock<std::mutex> lock(mutex_);
        while (written < size_bytes) {
            space_available_.wait(lock, [this]() {
                return stopped_ || size_ < buffer_.size();
            });
            if (stopped_) {
                return -1;
            }
            const std::size_t available = buffer_.size() - size_;
            const std::size_t chunk = std::min(available, size_bytes - written);
            WriteUnlocked(data + written, chunk);
            written += chunk;
            data_available_.notify_one();
        }
        return static_cast<int>(written);
    }

    std::size_t ReadOrSilence(uint8_t* target, std::size_t requested_bytes) {
        std::unique_lock<std::mutex> lock(mutex_);
        if (size_ == 0 && !stopped_) {
            data_available_.wait_for(lock, std::chrono::milliseconds(10), [this]() {
                return stopped_ || size_ > 0;
            });
        }
        if (size_ == 0) {
            std::memset(target, 0, requested_bytes);
            return stopped_ ? 0U : requested_bytes;
        }

        const std::size_t copied = ReadUnlocked(target, requested_bytes);
        if (copied < requested_bytes) {
            std::memset(target + copied, 0, requested_bytes - copied);
        }
        space_available_.notify_one();
        return requested_bytes;
    }

    void Reset() {
        std::scoped_lock<std::mutex> lock(mutex_);
        head_ = 0;
        tail_ = 0;
        size_ = 0;
        stopped_ = false;
        std::fill(buffer_.begin(), buffer_.end(), 0U);
    }

    void Stop() {
        {
            std::scoped_lock<std::mutex> lock(mutex_);
            stopped_ = true;
        }
        data_available_.notify_all();
        space_available_.notify_all();
    }

private:
    void WriteUnlocked(const uint8_t* data, std::size_t size_bytes) {
        std::size_t remaining = size_bytes;
        while (remaining > 0) {
            const std::size_t chunk = std::min(remaining, buffer_.size() - tail_);
            std::memcpy(buffer_.data() + tail_, data + (size_bytes - remaining), chunk);
            tail_ = (tail_ + chunk) % buffer_.size();
            size_ += chunk;
            remaining -= chunk;
        }
    }

    std::size_t ReadUnlocked(uint8_t* target, std::size_t requested_bytes) {
        const std::size_t readable = std::min(requested_bytes, size_);
        std::size_t remaining = readable;
        while (remaining > 0) {
            const std::size_t chunk = std::min(remaining, buffer_.size() - head_);
            std::memcpy(target + (readable - remaining), buffer_.data() + head_, chunk);
            head_ = (head_ + chunk) % buffer_.size();
            size_ -= chunk;
            remaining -= chunk;
        }
        return readable;
    }

    std::vector<uint8_t> buffer_;
    std::size_t head_ = 0;
    std::size_t tail_ = 0;
    std::size_t size_ = 0;
    bool stopped_ = false;
    std::mutex mutex_;
    std::condition_variable data_available_;
    std::condition_variable space_available_;
};
