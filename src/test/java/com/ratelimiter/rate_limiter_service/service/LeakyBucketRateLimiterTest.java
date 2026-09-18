package com.ratelimiter.rate_limiter_service.service;

import com.ratelimiter.rate_limiter_service.dto.CheckRequest;
import com.ratelimiter.rate_limiter_service.dto.CheckResponse;
import com.ratelimiter.rate_limiter_service.service.impl.LeakyBucketRateLimiter;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

class LeakyBucketRateLimiterTest {

    @Test
    void queueFillsThenBlocks() {
        LeakyBucketRateLimiter limiter = new LeakyBucketRateLimiter();
        ReflectionTestUtils.setField(limiter, "bucketSize", 2);
        ReflectionTestUtils.setField(limiter, "outflowRate", 2);
        ReflectionTestUtils.setField(limiter, "schedulerPoolSize", 1);
        limiter.init();

        CheckRequest request = new CheckRequest("user-1", "/api/test", "LEAKY_BUCKET");

        assertTrue(limiter.allow(request).isAllowed());
        assertTrue(limiter.allow(request).isAllowed());

        CheckResponse blocked = limiter.allow(request);
        assertFalse(blocked.isAllowed());
        assertEquals(0, blocked.getRemaining());
        assertTrue(blocked.getRetryAfter() >= 1L);

        limiter.shutdown();
    }

    @Test
    void drainerProcessesAtFixedRate() throws InterruptedException {
        LeakyBucketRateLimiter limiter = new LeakyBucketRateLimiter();
        ReflectionTestUtils.setField(limiter, "bucketSize", 2);
        ReflectionTestUtils.setField(limiter, "outflowRate", 2);
        ReflectionTestUtils.setField(limiter, "schedulerPoolSize", 1);
        limiter.init();

        CheckRequest request = new CheckRequest("user-1", "/api/test", "LEAKY_BUCKET");
        limiter.allow(request);
        limiter.allow(request);

        Thread.sleep(1500);

        CheckResponse next = limiter.allow(request);
        assertTrue(next.isAllowed());

        limiter.shutdown();
    }

    @Test
    void retryAfterMatchesDrainerInterval() {
        LeakyBucketRateLimiter limiter = new LeakyBucketRateLimiter();
        ReflectionTestUtils.setField(limiter, "bucketSize", 1);
        ReflectionTestUtils.setField(limiter, "outflowRate", 2);
        ReflectionTestUtils.setField(limiter, "schedulerPoolSize", 1);
        limiter.init();

        CheckRequest request = new CheckRequest("user-1", "/api/test", "LEAKY_BUCKET");
        limiter.allow(request);

        CheckResponse blocked = limiter.allow(request);
        assertFalse(blocked.isAllowed());
        assertEquals(1, blocked.getRetryAfter());

        limiter.shutdown();
    }
}
