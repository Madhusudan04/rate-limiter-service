package com.ratelimiter.rate_limiter_service.service;

import com.ratelimiter.rate_limiter_service.dto.CheckRequest;
import com.ratelimiter.rate_limiter_service.dto.CheckResponse;

public interface RateLimiterService {

    CheckResponse fixedWindowRateLimiter(CheckRequest request);
}
