package com.ratelimiter.rate_limiter_service.controller;

import com.ratelimiter.rate_limiter_service.dto.CheckRequest;
import com.ratelimiter.rate_limiter_service.dto.CheckResponse;
import com.ratelimiter.rate_limiter_service.service.RateLimiter;
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
        CheckResponse response = rateLimiterService.check(request);
        return response.isAllowed()
                ? ResponseEntity.ok(response)
                : ResponseEntity.status(429).body(response);
    }

}
