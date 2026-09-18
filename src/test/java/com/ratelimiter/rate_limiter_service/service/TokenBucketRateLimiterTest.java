package com.ratelimiter.rate_limiter_service.service;

import com.ratelimiter.rate_limiter_service.dto.CheckRequest;
import com.ratelimiter.rate_limiter_service.dto.CheckResponse;
import com.ratelimiter.rate_limiter_service.service.impl.TokenBucketRateLimiter;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

class TokenBucketRateLimiterTest {

    @Test
    void tokensRefillOverTime() throws InterruptedException {
        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter();
        ReflectionTestUtils.setField(limiter, "maxTokens", 3.0);
        ReflectionTestUtils.setField(limiter, "windowSeconds", 1);

        CheckRequest request = new CheckRequest("user-1", "/api/test", "TOKEN_BUCKET");

        for (int i = 0; i < 3; i++) {
            assertTrue(limiter.allow(request).isAllowed());
        }

        assertFalse(limiter.allow(request).isAllowed());

        Thread.sleep(1200);
        CheckResponse recovered = limiter.allow(request);
        assertTrue(recovered.isAllowed(), "tokens should refill after waiting");
    }

    @Test
    void burstAllowedUpToCapacity() {
        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter();
        ReflectionTestUtils.setField(limiter, "maxTokens", 3.0);
        ReflectionTestUtils.setField(limiter, "windowSeconds", 10);

        CheckRequest request = new CheckRequest("user-1", "/api/test", "TOKEN_BUCKET");

        for (int i = 0; i < 3; i++) {
            assertTrue(limiter.allow(request).isAllowed());
        }

        CheckResponse blocked = limiter.allow(request);
        assertFalse(blocked.isAllowed());
        assertTrue(blocked.getRetryAfter() >= 1);
    }

    @Test
    void gradualExhaustionWorks() {
        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter();
        ReflectionTestUtils.setField(limiter, "maxTokens", 2.0);
        ReflectionTestUtils.setField(limiter, "windowSeconds", 10);

        CheckRequest request = new CheckRequest("user-1", "/api/test", "TOKEN_BUCKET");

        assertTrue(limiter.allow(request).isAllowed());
        assertTrue(limiter.allow(request).isAllowed());
        CheckResponse blocked = limiter.allow(request);

        assertFalse(blocked.isAllowed());
        assertTrue(blocked.getRemaining() >= 0);
    }

    @Test
    void retryAfterBasedOnRefillRate() {
        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter();
        ReflectionTestUtils.setField(limiter, "maxTokens", 4.0);
        ReflectionTestUtils.setField(limiter, "windowSeconds", 4);

        CheckRequest request = new CheckRequest("user-1", "/api/test", "TOKEN_BUCKET");

        for (int i = 0; i < 4; i++) {
            assertTrue(limiter.allow(request).isAllowed());
        }

        CheckResponse blocked = limiter.allow(request);
        assertFalse(blocked.isAllowed());
        assertTrue(blocked.getRetryAfter() >= 1L);
    }
}
