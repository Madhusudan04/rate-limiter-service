package com.ratelimiter.rate_limiter_service.service.impl;

import com.ratelimiter.rate_limiter_service.dto.CheckRequest;
import com.ratelimiter.rate_limiter_service.dto.CheckResponse;
import com.ratelimiter.rate_limiter_service.dto.Bucket;
import com.ratelimiter.rate_limiter_service.service.RateLimiterService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class FixedWindowRateLimiter implements RateLimiterService {

    Map<String, Map<String, Bucket>> bucketMap = new ConcurrentHashMap<>();

    @Value("${maxTokens:4}")
    private int maxTokens;

    @Value("${windowSeconds:60}")
    private int windowSeconds;

    public CheckResponse allow(CheckRequest request){
        Instant currentTime = Instant.now();
        String clientId = request.getClientId();
        String endpoint = request.getEndpoint();
        CheckResponse response = new CheckResponse();
        response.setAllowed(true);

        Map<String, Bucket> endpointMap = bucketMap.computeIfAbsent(clientId, k -> new ConcurrentHashMap<>());

        Bucket bucket = endpointMap.computeIfAbsent(endpoint, k -> new Bucket(maxTokens,currentTime));

        synchronized (bucket){
            Instant  windowStart = bucket.getLocalTime();
            Instant  windowEnd = windowStart.plusSeconds(windowSeconds);

            if(currentTime.isAfter(windowEnd)){
                bucket.setLocalTime(currentTime);
                bucket.setToken(maxTokens);
            }

            if(bucket.getToken() <= 0){
                long retryAfterSeconds = Duration.between(currentTime, windowEnd).getSeconds();
                response.setAllowed(false);
                response.setRetryAfter((int) Math.max(0, retryAfterSeconds));
            }else{
                bucket.setToken(bucket.getToken() - 1);
                response.setRemaining(bucket.getToken());
            }
        }

        return response;
    }
}
