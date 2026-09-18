package com.ratelimiter.rate_limiter_service.storage;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class InMemoryStorageTest {

    @Test
    void incrementWorksAndRejectsWhenLimitReached() {
        InMemoryStorage storage = new InMemoryStorage();

        assertTrue(storage.allow("user:api", 2));
        assertTrue(storage.allow("user:api", 2));
        assertFalse(storage.allow("user:api", 2));
        assertEquals(2, storage.getCount("user:api"));
    }

    @Test
    void ttlExpiryRemovesExpiredCounter() throws Exception {
        InMemoryStorage storage = new InMemoryStorage();
        storage.allow("user:expired", 2);

        Field countersField = InMemoryStorage.class.getDeclaredField("counters");
        countersField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, Object> counters = (Map<String, Object>) countersField.get(storage);
        Object counter = counters.get("user:expired");

        Field expiryField = counter.getClass().getDeclaredField("expiryMillis");
        expiryField.setAccessible(true);
        expiryField.setLong(counter, System.currentTimeMillis() - 1000L);

        assertEquals(0, storage.getCount("user:expired"));
    }

    @Test
    void cleanupExpiredEntriesRemovesStaleKeys() throws Exception {
        InMemoryStorage storage = new InMemoryStorage();
        storage.allow("user:cleanup", 2);

        Field countersField = InMemoryStorage.class.getDeclaredField("counters");
        countersField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, Object> counters = (Map<String, Object>) countersField.get(storage);
        Object counter = counters.get("user:cleanup");

        Field expiryField = counter.getClass().getDeclaredField("expiryMillis");
        expiryField.setAccessible(true);
        expiryField.setLong(counter, System.currentTimeMillis() - 1000L);

        storage.cleanupExpiredEntries();

        assertEquals(0, storage.getCount("user:cleanup"));
    }
}
