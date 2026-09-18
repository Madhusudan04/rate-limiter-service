package com.ratelimiter.rate_limiter_service.storage;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class InMemoryStorage implements RateLimiterStorage {
    private static final long DEFAULT_TTL_MILLIS = 60_000L;
    private final Map<String, CounterEntry> counters = new ConcurrentHashMap<>();

    @Override
    public boolean allow(String key, int limit) {
        if (key == null || key.isBlank()) {
            return false;
        }

        long now = System.currentTimeMillis();
        CounterEntry entry = counters.compute(key, (k, current) -> {
            if (current != null && current.isExpired(now)) {
                return null;
            }
            if (current == null) {
                return new CounterEntry(new AtomicInteger(0), now + DEFAULT_TTL_MILLIS);
            }
            current.refreshExpiry(now + DEFAULT_TTL_MILLIS);
            return current;
        });

        if (entry == null) {
            entry = new CounterEntry(new AtomicInteger(0), now + DEFAULT_TTL_MILLIS);
            counters.put(key, entry);
        }

        int current;
        do {
            current = entry.count.get();
            if (current >= limit) {
                return false;
            }
        } while (!entry.count.compareAndSet(current, current + 1));

        entry.refreshExpiry(System.currentTimeMillis() + DEFAULT_TTL_MILLIS);
        return true;
    }

    @Override
    public int getCount(String key) {
        if (key == null || key.isBlank()) {
            return 0;
        }
        CounterEntry entry = counters.get(key);
        if (entry == null || entry.isExpired(System.currentTimeMillis())) {
            counters.remove(key);
            return 0;
        }
        return entry.count.get();
    }

    @Override
    public void reset(String key) {
        counters.remove(key);
    }

    @Scheduled(fixedDelay = 30_000L)
    public void cleanupExpiredEntries() {
        long now = System.currentTimeMillis();
        counters.entrySet().removeIf(entry -> entry.getValue().isExpired(now));
    }

    private static final class CounterEntry {
        private final AtomicInteger count;
        private volatile long expiryMillis;

        private CounterEntry(AtomicInteger count, long expiryMillis) {
            this.count = count;
            this.expiryMillis = expiryMillis;
        }

        private boolean isExpired(long now) {
            return now >= expiryMillis;
        }

        private void refreshExpiry(long expiryMillis) {
            this.expiryMillis = expiryMillis;
        }
    }
}
