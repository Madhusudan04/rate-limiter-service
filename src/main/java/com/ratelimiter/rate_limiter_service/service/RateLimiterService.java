package com.ratelimiter.rate_limiter_service.service;

import com.ratelimiter.rate_limiter_service.dto.CheckRequest;
import com.ratelimiter.rate_limiter_service.dto.CheckResponse;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class RateLimiterService {

    private final Map<String, RateLimiter> rateLimiterMap = new HashMap<>();

    // Spring injects List of all RateLimiter implementations
    public RateLimiterService(List<RateLimiter> rateLimiters){
        for(RateLimiter limiter : rateLimiters){
            rateLimiterMap.put(limiter.getAlgorithmName(), limiter);
        }
    }

    public CheckResponse check(CheckRequest request){
        String algorithm = request.getAlgorithm();

        RateLimiter rateLimiter = rateLimiterMap.getOrDefault(
                algorithm,
                rateLimiterMap.get("FIXED_WINDOW") // default
        );

        return rateLimiter.allow(request);
    }
}