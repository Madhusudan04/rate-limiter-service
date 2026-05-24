package com.ratelimiter.rate_limiter_service.controller;

import com.ratelimiter.rate_limiter_service.dto.CheckRequest;
import com.ratelimiter.rate_limiter_service.dto.CheckResponse;
import com.ratelimiter.rate_limiter_service.service.RateLimiterService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RateLimitController {

    @Autowired
    private RateLimiterService rateLimiterService;

    @PostMapping("/api/v1/check")
    ResponseEntity<CheckResponse> rateLimiter(@RequestBody CheckRequest request){
        CheckResponse checkResponse = rateLimiterService.fixedWindowRateLimiter(request);
        if(checkResponse.isAllowed()){
            return ResponseEntity.ok(checkResponse);
        }
        return ResponseEntity.status(429).body(checkResponse);
    }

}
