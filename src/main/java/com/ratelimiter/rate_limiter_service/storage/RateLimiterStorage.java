package com.ratelimiter.rate_limiter_service.storage;

public interface RateLimiterStorage {
    boolean allow(String key, int limit);
    int getCount(String key);
    void reset(String key);
}
