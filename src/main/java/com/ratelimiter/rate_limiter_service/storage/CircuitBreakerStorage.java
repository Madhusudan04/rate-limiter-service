package com.ratelimiter.rate_limiter_service.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class CircuitBreakerStorage implements RateLimiterStorage {

    public enum CircuitState {
        CLOSED,
        OPEN,
        HALF_OPEN
    }

    private final RedisStorage redisStorage;
    private final InMemoryStorage fallbackStorage;
    private final int failureThreshold;
    private final Duration openDuration;
    private final AtomicInteger failureCount = new AtomicInteger(0);
    private final AtomicReference<CircuitState> state = new AtomicReference<>(CircuitState.CLOSED);
    private volatile long openedAtMillis = 0L;

    public CircuitBreakerStorage(RedisStorage redisStorage,
                                InMemoryStorage fallbackStorage,
                                @Value("${circuit.breaker.failure.threshold:5}") int failureThreshold,
                                @Value("${circuit.breaker.timeout.seconds:30}") long timeoutSeconds) {
        this.redisStorage = redisStorage;
        this.fallbackStorage = fallbackStorage;
        this.failureThreshold = failureThreshold;
        this.openDuration = Duration.ofSeconds(timeoutSeconds);
        this.openedAtMillis = 0L;
    }

    @Override
    public boolean allow(String key, int limit) {
        if (state.get() == CircuitState.OPEN) {
            long now = System.currentTimeMillis();
            if (openedAtMillis == 0L || now - openedAtMillis < openDuration.toMillis()) {
                return fallbackStorage.allow(key, limit);
            }
            state.set(CircuitState.HALF_OPEN);
        }

        if (state.get() == CircuitState.HALF_OPEN) {
            try {
                boolean allowed = redisStorage.allow(key, limit);
                if (allowed) {
                    state.set(CircuitState.CLOSED);
                    failureCount.set(0);
                    return true;
                }
                state.set(CircuitState.OPEN);
                openedAtMillis = System.currentTimeMillis();
                return fallbackStorage.allow(key, limit);
            } catch (Exception ex) {
                state.set(CircuitState.OPEN);
                openedAtMillis = System.currentTimeMillis();
                return fallbackStorage.allow(key, limit);
            }
        }

        try {
            boolean allowed = redisStorage.allow(key, limit);
            if (allowed) {
                failureCount.set(0);
                return true;
            }
            return false;
        } catch (Exception ex) {
            int currentFailures = failureCount.incrementAndGet();
            if (currentFailures >= failureThreshold) {
                state.set(CircuitState.OPEN);
                openedAtMillis = System.currentTimeMillis();
            }
            return fallbackStorage.allow(key, limit);
        }
    }

    @Override
    public int getCount(String key) {
        if (state.get() == CircuitState.OPEN) {
            return fallbackStorage.getCount(key);
        }
        try {
            return redisStorage.getCount(key);
        } catch (Exception ex) {
            return fallbackStorage.getCount(key);
        }
    }

    @Override
    public void reset(String key) {
        try {
            redisStorage.reset(key);
        } catch (Exception ignored) {
            // Ignore; fallback path is already in memory
        }
        fallbackStorage.reset(key);
    }
}
