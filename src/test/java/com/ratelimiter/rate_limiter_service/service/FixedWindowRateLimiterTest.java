package com.ratelimiter.rate_limiter_service.service;

import com.ratelimiter.rate_limiter_service.dto.Bucket;
import com.ratelimiter.rate_limiter_service.dto.CheckRequest;
import com.ratelimiter.rate_limiter_service.dto.CheckResponse;
import com.ratelimiter.rate_limiter_service.service.impl.FixedWindowRateLimiter;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class FixedWindowRateLimiterTest {

    @Test
    void firstRequestAllowed() {
        FixedWindowRateLimiter limiter = new FixedWindowRateLimiter();
        ReflectionTestUtils.setField(limiter, "maxTokens", 2);
        ReflectionTestUtils.setField(limiter, "windowSeconds", 60);

        CheckResponse response = limiter.allow(new CheckRequest("user-1", "/api/test", "FIXED_WINDOW"));

        assertTrue(response.isAllowed());
        assertEquals(1, response.getRemaining());
    }

    @Test
    void afterLimitRequestsBlocked() {
        FixedWindowRateLimiter limiter = new FixedWindowRateLimiter();
        ReflectionTestUtils.setField(limiter, "maxTokens", 2);
        ReflectionTestUtils.setField(limiter, "windowSeconds", 60);

        limiter.allow(new CheckRequest("user-1", "/api/test", "FIXED_WINDOW"));
        limiter.allow(new CheckRequest("user-1", "/api/test", "FIXED_WINDOW"));

        CheckResponse blocked = limiter.allow(new CheckRequest("user-1", "/api/test", "FIXED_WINDOW"));

        assertFalse(blocked.isAllowed());
        assertEquals(0, blocked.getRemaining());
        assertTrue(blocked.getRetryAfter() >= 1);
    }

    @Test
    void windowResetClearsTokens() throws Exception {
        FixedWindowRateLimiter limiter = new FixedWindowRateLimiter();
        ReflectionTestUtils.setField(limiter, "maxTokens", 2);
        ReflectionTestUtils.setField(limiter, "windowSeconds", 60);

        limiter.allow(new CheckRequest("user-1", "/api/test", "FIXED_WINDOW"));
        limiter.allow(new CheckRequest("user-1", "/api/test", "FIXED_WINDOW"));

        @SuppressWarnings("unchecked")
        Map<String, Map<String, Bucket>> bucketMap =
                (Map<String, Map<String, Bucket>>) ReflectionTestUtils.getField(limiter, "bucketMap");
        Bucket bucket = bucketMap.get("user-1").get("/api/test");
        bucket.setLocalTime(Instant.now().minusSeconds(120));
        bucket.setToken(0);

        CheckResponse response = limiter.allow(new CheckRequest("user-1", "/api/test", "FIXED_WINDOW"));

        assertTrue(response.isAllowed());
        assertEquals(1, response.getRemaining());
    }

    @Test
    void retryAfterCalculatedCorrectly() {
        FixedWindowRateLimiter limiter = new FixedWindowRateLimiter();
        ReflectionTestUtils.setField(limiter, "maxTokens", 1);
        ReflectionTestUtils.setField(limiter, "windowSeconds", 60);

        limiter.allow(new CheckRequest("user-1", "/api/test", "FIXED_WINDOW"));
        CheckResponse response = limiter.allow(new CheckRequest("user-1", "/api/test", "FIXED_WINDOW"));

        assertFalse(response.isAllowed());
        assertTrue(response.getRetryAfter() >= 1L);
        assertEquals(0, response.getRemaining());
    }
}
