package com.ratelimiter.rate_limiter_service.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.Collections;

@Component
public class RedisStorage implements RateLimiterStorage {

    private static final String ALLOW_SCRIPT =
            "local count = redis.call('INCR', KEYS[1]); " +
            "if count == 1 then redis.call('EXPIRE', KEYS[1], ARGV[2]); end; " +
            "if count > tonumber(ARGV[1]) then redis.call('DECR', KEYS[1]); return 0; end; " +
            "return 1;";

    private final StringRedisTemplate redisTemplate;
    private final boolean enabled;
    private final int defaultTtlSeconds;
    private final DefaultRedisScript<Long> allowScript;

    public RedisStorage(StringRedisTemplate redisTemplate,
                       @Value("${spring.redis.host:}") String redisHost,
                       @Value("${windowSeconds:60}") int defaultTtlSeconds) {
        this.redisTemplate = redisTemplate;
        this.enabled = redisHost != null && !redisHost.isBlank();
        this.defaultTtlSeconds = Math.max(1, defaultTtlSeconds);
        this.allowScript = new DefaultRedisScript<>();
        this.allowScript.setScriptText(ALLOW_SCRIPT);
        this.allowScript.setResultType(Long.class);
    }

    @Override
    public boolean allow(String key, int limit) {
        if (!enabled || redisTemplate == null || key == null || key.isBlank()) {
            return false;
        }

        Long allowed = redisTemplate.execute(
                allowScript,
                Collections.singletonList(key),
                String.valueOf(limit),
                String.valueOf(defaultTtlSeconds));
        return Boolean.TRUE.equals(allowed != null && allowed == 1L);
    }

    @Override
    public int getCount(String key) {
        if (!enabled || redisTemplate == null || key == null || key.isBlank()) {
            return 0;
        }
        String value = redisTemplate.opsForValue().get(key);
        return value == null ? 0 : Integer.parseInt(value);
    }

    @Override
    public void reset(String key) {
        if (enabled && redisTemplate != null && key != null && !key.isBlank()) {
            redisTemplate.delete(key);
        }
    }
}
