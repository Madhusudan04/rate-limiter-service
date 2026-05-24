package com.ratelimiter.rate_limiter_service.service.impl;

import com.ratelimiter.rate_limiter_service.dto.CheckRequest;
import com.ratelimiter.rate_limiter_service.dto.CheckResponse;
import com.ratelimiter.rate_limiter_service.dto.Bucket;
import com.ratelimiter.rate_limiter_service.service.RateLimiterService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class FixedWindowRateLimiter implements RateLimiterService {

    ConcurrentHashMap<String, Map<String, Bucket>> bucketMap = new ConcurrentHashMap<>();

    @Value("${maxTokens}")
    private int maxTokens;

    public CheckResponse fixedWindowRateLimiter(CheckRequest request){
        LocalTime currentTime = LocalTime.now();
        String clientId = request.getClientId();
        String endpoint = request.getEndpoint();
        CheckResponse response = new CheckResponse();
        response.setAllowed(true);
            if(bucketMap.containsKey(clientId)){
                Bucket existingBucket = bucketMap.get(clientId).get(endpoint);
                if(existingBucket == null){
                    Bucket newBucket = new Bucket(maxTokens - 1, currentTime);
                    bucketMap.get(clientId).put(endpoint, newBucket);
                    response.setRemaining(newBucket.getToken());
                    return response;
                }
                synchronized (existingBucket){
                    if(currentTime.isAfter(existingBucket.getLocalTime().plusMinutes(1))){
                        existingBucket.setToken(maxTokens);
                        existingBucket.setLocalTime(existingBucket.getLocalTime().plusMinutes(1));
                    }
                    if(existingBucket.getToken() == 0){
                        long elapsed = Duration.between(existingBucket.getLocalTime(), currentTime).getSeconds();
                        response.setRetryAfter(Math.max(0, 60 - elapsed));
                        response.setAllowed(false);
                        return response;
                    }
                    existingBucket.setToken(existingBucket.getToken()-1);
                    response.setRemaining(existingBucket.getToken());
                    bucketMap.get(clientId).put(endpoint,existingBucket);
                }

            }else {
                Bucket newBucket = new Bucket(maxTokens-1,currentTime);
                bucketMap.computeIfAbsent(clientId, k -> {
                    Map<String, Bucket> endpointMap = new ConcurrentHashMap<>();
                    endpointMap.put(endpoint, newBucket);
                    return endpointMap;
                });
                response.setRemaining(newBucket.getToken());
            }

        return response;
    }
}
