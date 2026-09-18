package com.ratelimiter.rate_limiter_service.service;

import com.ratelimiter.rate_limiter_service.dto.CheckRequest;
import com.ratelimiter.rate_limiter_service.dto.CheckResponse;
import com.ratelimiter.rate_limiter_service.service.impl.SlidingWindowCounterRateLimiter;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

class SlidingWindowCounterRateLimiterTest {

    @Test
    void weightedFormulaAllowsWithinLimit() {
        SlidingWindowCounterRateLimiter limiter = new SlidingWindowCounterRateLimiter();
        ReflectionTestUtils.setField(limiter, "maxRequests", 2);
        ReflectionTestUtils.setField(limiter, "windowSeconds", 1);

        CheckRequest request = new CheckRequest("user-1", "/api/test", "SLIDING_WINDOW_COUNTER");

        assertTrue(limiter.allow(request).isAllowed());
        assertTrue(limiter.allow(request).isAllowed());

        CheckResponse blocked = limiter.allow(request);
        assertFalse(blocked.isAllowed());
        assertTrue(blocked.getRetryAfter() >= 1L);
    }

    @Test
    void boundaryCaseDoesNotAllowExtraRequest() {
        SlidingWindowCounterRateLimiter limiter = new SlidingWindowCounterRateLimiter();
        ReflectionTestUtils.setField(limiter, "maxRequests", 1);
        ReflectionTestUtils.setField(limiter, "windowSeconds", 1);

        CheckRequest request = new CheckRequest("user-1", "/api/test", "SLIDING_WINDOW_COUNTER");

        assertTrue(limiter.allow(request).isAllowed());
        CheckResponse blocked = limiter.allow(request);

        assertFalse(blocked.isAllowed());
        assertEquals(0, blocked.getRemaining());
    }

    @Test
    void retryAfterIsPositiveWhenBlocked() {
        SlidingWindowCounterRateLimiter limiter = new SlidingWindowCounterRateLimiter();
        ReflectionTestUtils.setField(limiter, "maxRequests", 2);
        ReflectionTestUtils.setField(limiter, "windowSeconds", 1);

        CheckRequest request = new CheckRequest("user-1", "/api/test", "SLIDING_WINDOW_COUNTER");
        limiter.allow(request);
        limiter.allow(request);

        CheckResponse blocked = limiter.allow(request);
        assertTrue(blocked.getRetryAfter() >= 1L);
    }
}
