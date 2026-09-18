package com.ratelimiter.rate_limiter_service.storage;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CircuitBreakerStorageTest {

    @Test
    void closedStateUsesRedisWhenHealthy() {
        RedisStorage redis = mock(RedisStorage.class);
        InMemoryStorage fallback = new InMemoryStorage();
        CircuitBreakerStorage storage = new CircuitBreakerStorage(redis, fallback, 2, 1);

        when(redis.allow("key-1", 3)).thenReturn(true);

        assertTrue(storage.allow("key-1", 3));
    }

    @Test
    void closedStateFallsBackAfterFailuresAndOpensCircuit() {
        RedisStorage redis = mock(RedisStorage.class);
        InMemoryStorage fallback = new InMemoryStorage();
        CircuitBreakerStorage storage = new CircuitBreakerStorage(redis, fallback, 2, 1);

        when(redis.allow("key-2", 3)).thenThrow(new RuntimeException("redis down"));

        assertTrue(storage.allow("key-2", 3));
        assertTrue(storage.allow("key-2", 3));

        AtomicReference<CircuitBreakerStorage.CircuitState> state =
                (AtomicReference<CircuitBreakerStorage.CircuitState>) ReflectionTestUtils.getField(storage, "state");
        assertEquals(CircuitBreakerStorage.CircuitState.OPEN, state.get());
    }

    @Test
    void openStateUsesFallbackStorage() {
        RedisStorage redis = mock(RedisStorage.class);
        InMemoryStorage fallback = new InMemoryStorage();
        CircuitBreakerStorage storage = new CircuitBreakerStorage(redis, fallback, 2, 1);

        AtomicReference<CircuitBreakerStorage.CircuitState> state =
                (AtomicReference<CircuitBreakerStorage.CircuitState>) ReflectionTestUtils.getField(storage, "state");
        state.set(CircuitBreakerStorage.CircuitState.OPEN);
        ReflectionTestUtils.setField(storage, "openedAtMillis", System.currentTimeMillis());

        assertTrue(storage.allow("key-3", 2));
        assertTrue(fallback.getCount("key-3") >= 0);
    }

    @Test
    void halfOpenStateRecoversToClosedOnSuccess() {
        RedisStorage redis = mock(RedisStorage.class);
        InMemoryStorage fallback = new InMemoryStorage();
        CircuitBreakerStorage storage = new CircuitBreakerStorage(redis, fallback, 2, 1);

        AtomicReference<CircuitBreakerStorage.CircuitState> state =
                (AtomicReference<CircuitBreakerStorage.CircuitState>) ReflectionTestUtils.getField(storage, "state");
        state.set(CircuitBreakerStorage.CircuitState.HALF_OPEN);
        when(redis.allow("key-4", 2)).thenReturn(true);

        assertTrue(storage.allow("key-4", 2));
        assertEquals(CircuitBreakerStorage.CircuitState.CLOSED, state.get());
    }
}
