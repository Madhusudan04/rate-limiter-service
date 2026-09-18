package com.ratelimiter.rate_limiter_service.controller;

import com.ratelimiter.rate_limiter_service.dto.CheckRequest;
import com.ratelimiter.rate_limiter_service.dto.CheckResponse;
import com.ratelimiter.rate_limiter_service.service.RateLimiterService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RateLimitController {

    private final RateLimiterService rateLimiterService;

    public RateLimitController(RateLimiterService rateLimiterService) {
        this.rateLimiterService = rateLimiterService;
    }

    @PostMapping("/api/v1/check")
    ResponseEntity<CheckResponse> rateLimiter(@Valid @RequestBody CheckRequest request) {
        if (request == null
                || request.getClientId() == null
                || request.getClientId().isBlank()
                || request.getEndpoint() == null
                || request.getEndpoint().isBlank()) {
            return ResponseEntity.badRequest().body(
                    CheckResponse.invalidRequest("clientId and endpoint are required"));
        }

        CheckResponse response = rateLimiterService.check(request);
        return response.isAllowed()
                ? ResponseEntity.ok(response)
                : ResponseEntity.status(429).body(response);
    }
}
