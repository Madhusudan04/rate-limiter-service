package com.ratelimiter.rate_limiter_service.service.impl;

import com.ratelimiter.rate_limiter_service.dto.Bucket;
import com.ratelimiter.rate_limiter_service.dto.CheckRequest;
import com.ratelimiter.rate_limiter_service.dto.CheckResponse;
import com.ratelimiter.rate_limiter_service.service.RateLimiter;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class FixedWindowRateLimiter implements RateLimiter {

    Map<String, Map<String, Bucket>> bucketMap = new ConcurrentHashMap<>();

    @Value("${maxTokens:4}")
    private int maxTokens;

    @Value("${windowSeconds:60}")
    private int windowSeconds;

    @PostConstruct
    void validateConfiguration() {
        if (maxTokens <= 0 || windowSeconds <= 0) {
            throw new IllegalStateException("maxTokens and windowSeconds must be greater than zero");
        }
    }

    public CheckResponse allow(CheckRequest request){
        Instant currentTime = Instant.now();
        String clientId = request.getClientId();
        String endpoint = request.getEndpoint();
        CheckResponse response = new CheckResponse();

        Map<String, Bucket> endpointMap = bucketMap.computeIfAbsent(clientId, k -> new ConcurrentHashMap<>());

        Bucket bucket = endpointMap.computeIfAbsent(endpoint, k -> new Bucket(maxTokens,currentTime));

        synchronized (bucket){
            Instant  windowStart = bucket.getLocalTime();
            Instant  windowEnd = windowStart.plusSeconds(windowSeconds);

            if(!currentTime.isBefore(windowEnd)){
                long elapsedWindows = Duration.between(windowStart, currentTime).getSeconds() / windowSeconds;
                bucket.setLocalTime(windowStart.plusSeconds(Math.max(1L, elapsedWindows) * windowSeconds));
                bucket.setToken(maxTokens);
                windowEnd = bucket.getLocalTime().plusSeconds(windowSeconds);
            }

            if(bucket.getToken() <= 0){
                long retryAfterSeconds = (long) Math.ceil(
                        Duration.between(currentTime, windowEnd).toMillis() / 1000.0);
                response.setAllowed(false);
                response.setRemaining(0);
                response.setRetryAfter(Math.max(1, retryAfterSeconds));
                response.setMessage("Rate limit exceeded");
            }else{
                bucket.setToken(bucket.getToken() - 1);
                response.setAllowed(true);
                response.setRemaining(bucket.getToken());
                response.setRetryAfter(0);
                response.setMessage("Request allowed");
            }
        }

        return response;
    }

    @Override
    public String getAlgorithmName() {
        return "FIXED_WINDOW";
    }

}
