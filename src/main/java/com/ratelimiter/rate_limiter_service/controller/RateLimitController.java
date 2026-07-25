package com.ratelimiter.rate_limiter_service.controller;

import com.ratelimiter.rate_limiter_service.dto.CheckRequest;
import com.ratelimiter.rate_limiter_service.dto.CheckResponse;
import com.ratelimiter.rate_limiter_service.service.impl.FixedWindowRateLimiter;
import com.ratelimiter.rate_limiter_service.service.impl.TokenBucketRateLimiter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RateLimitController {


    @Autowired
    private TokenBucketRateLimiter tokenBucketRateLimiter;

    @Autowired
    private FixedWindowRateLimiter fixedWindowRateLimiter;


    @PostMapping("/api/v1/check")
    ResponseEntity<CheckResponse> rateLimiter(@RequestBody CheckRequest request){
        CheckResponse checkResponse = fixedWindowRateLimiter.allow(request);
        if(checkResponse.isAllowed()){
            return ResponseEntity.ok(checkResponse);
        }
        return ResponseEntity.status(429).body(checkResponse);
    }

    @PostMapping("/api/v2/check")
    ResponseEntity<CheckResponse> tokenBucketRateLimiter(@RequestBody CheckRequest request){
        CheckResponse checkResponse = tokenBucketRateLimiter.allow(request);
        if(checkResponse.isAllowed()){
            return ResponseEntity.ok(checkResponse);
        }
        return ResponseEntity.status(429).body(checkResponse);
    }

}
