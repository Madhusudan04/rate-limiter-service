package com.ratelimiter.rate_limiter_service.service.impl;

import com.ratelimiter.rate_limiter_service.dto.CheckRequest;
import com.ratelimiter.rate_limiter_service.dto.CheckResponse;
import com.ratelimiter.rate_limiter_service.dto.SlidingWindow;
import com.ratelimiter.rate_limiter_service.service.RateLimiter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import jakarta.annotation.PostConstruct;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class SlidingWindowCounterRateLimiter implements RateLimiter {

    private final Map<String, Map<String, SlidingWindow>> windowMap = new ConcurrentHashMap<>();

    @Value("${slidingWindowMaxRequests:4}")
    private int maxRequests;

    @Value("${windowSeconds:60}")
    private int windowSeconds;

    @PostConstruct
    public void validateConfiguration() {
        if (maxRequests <= 0 || windowSeconds <= 0) {
            throw new IllegalStateException(
                    "slidingWindowMaxRequests and windowSeconds must be greater than zero");
        }
    }

    @Override
    public CheckResponse allow(CheckRequest request) {
        long nowMillis = Instant.now().toEpochMilli();
        String clientId = request.getClientId();
        String endpoint = request.getEndpoint();

        Map<String, SlidingWindow> endpointMap =
                windowMap.computeIfAbsent(clientId, key -> new ConcurrentHashMap<>());
        SlidingWindow window = endpointMap.computeIfAbsent(
                endpoint,
                key -> new SlidingWindow(nowMillis, 0, 0));

        synchronized (window) {
            long windowDurationMillis = windowSeconds * 1000L;
            rollWindowForward(window, nowMillis, windowDurationMillis);

            double elapsedRatio =
                    (double) (nowMillis - window.getWindowStartMillis()) / windowDurationMillis;
            double previousWindowWeight = 1.0 - elapsedRatio;
            double estimatedCount =
                    window.getPreviousWindowCount() * previousWindowWeight
                            + window.getCurrentWindowCount();

            CheckResponse response = new CheckResponse();
            if (estimatedCount >= maxRequests) {
                response.setAllowed(false);
                response.setRemaining(0);
                response.setRetryAfter(calculateRetryAfter(window, nowMillis, windowDurationMillis));
                response.setMessage("Rate limit exceeded");
                return response;
            }

            window.setCurrentWindowCount(window.getCurrentWindowCount() + 1);
            response.setAllowed(true);
            response.setRetryAfter(0);
            response.setRemaining(Math.max(0, (int) Math.floor(maxRequests - estimatedCount - 1)));
            response.setMessage("Request allowed");
            return response;
        }
    }

    @Override
    public String getAlgorithmName() {
        return "SLIDING_WINDOW_COUNTER";
    }

    private void rollWindowForward(
            SlidingWindow window, long nowMillis, long windowDurationMillis) {
        // Advance window if elapsed time crosses window boundary
        long elapsedWindows =
                (nowMillis - window.getWindowStartMillis()) / windowDurationMillis;

        if (elapsedWindows <= 0) {
            return;
        }

        if (elapsedWindows == 1) {
            // If exactly 1 window passed, previous becomes current
            window.setPreviousWindowCount(window.getCurrentWindowCount());
        } else {
            // If 2+ windows passed, reset previous (too old)
            window.setPreviousWindowCount(0);
        }
        window.setCurrentWindowCount(0);
        window.setWindowStartMillis(
                window.getWindowStartMillis() + elapsedWindows * windowDurationMillis);
    }

    /**
     * Calculates retry time for three situations:
     * 1. previous window is still heavy enough to block admission,
     * 2. current window is full and new requests must wait for the next window,
     * 3. no previous window data exists, so the wait is driven by the current window boundary.
     */
    private long calculateRetryAfter(
            SlidingWindow window, long nowMillis, long windowDurationMillis) {
        double elapsedRatio =
                (double) (nowMillis - window.getWindowStartMillis()) / windowDurationMillis;
        double previousWindowWeight = 1.0 - elapsedRatio;
        double weightedPreviousCount = window.getPreviousWindowCount() * previousWindowWeight;

        if (weightedPreviousCount >= maxRequests) {
            long nextWindowMillis =
                    window.getWindowStartMillis() + windowDurationMillis - nowMillis;
            return Math.max(1, (long) Math.ceil(nextWindowMillis / 1000.0));
        }

        double requiredWeight =
                maxRequests - window.getCurrentWindowCount();
        if (window.getPreviousWindowCount() > 0) {
            double targetElapsedRatio =
                    1.0 - (requiredWeight / window.getPreviousWindowCount());
            long retryMillis =
                    (long) Math.ceil(
                            targetElapsedRatio * windowDurationMillis
                                    - (nowMillis - window.getWindowStartMillis()));
            return Math.max(1, (long) Math.ceil(retryMillis / 1000.0));
        }

        return Math.max(
                1,
                (long)
                        Math.ceil(
                                (window.getWindowStartMillis()
                                        + windowDurationMillis
                                        - nowMillis)
                                        / 1000.0));
    }
}
