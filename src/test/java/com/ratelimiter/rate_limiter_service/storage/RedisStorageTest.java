package com.ratelimiter.rate_limiter_service.storage;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.junit.jupiter.api.Assertions.*;

class RedisStorageTest {

    @Test
    void disabledRedisReturnsFalse() {
        RedisStorage storage = new RedisStorage(new StringRedisTemplate(), "", 60);

        assertFalse(storage.allow("key", 3));
        assertEquals(0, storage.getCount("key"));
    }

    @Test
    void resetDoesNotThrowWhenStorageDisabled() {
        RedisStorage storage = new RedisStorage(new StringRedisTemplate(), "", 60);

        assertDoesNotThrow(() -> storage.reset("key"));
    }
}
