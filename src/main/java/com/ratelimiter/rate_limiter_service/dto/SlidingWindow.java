package com.ratelimiter.rate_limiter_service.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SlidingWindow {
    private long windowStartMillis;
    private long previousWindowCount;
    private long currentWindowCount;
}
