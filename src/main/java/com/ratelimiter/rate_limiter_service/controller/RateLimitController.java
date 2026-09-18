package com.ratelimiter.rate_limiter_service.controller;

import com.ratelimiter.rate_limiter_service.dto.CheckRequest;
import com.ratelimiter.rate_limiter_service.dto.CheckResponse;
import com.ratelimiter.rate_limiter_service.service.RateLimiterService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for evaluating whether a request should be allowed under the active
 * rate-limiting policy for a specific client and endpoint.
 *
 * <p>The controller accepts a check request, validates the required fields, resolves the
 * request through the rate limiter service, and returns either a successful 200 response
 * or a 429 Too Many Requests response when the configured limit is exceeded.</p>
 */
@RestController
public class RateLimitController {

    private final RateLimiterService rateLimiterService;

    public RateLimitController(RateLimiterService rateLimiterService) {
        this.rateLimiterService = rateLimiterService;
    }

    /**
     * Checks whether a request from a client to an endpoint is allowed.
     *
     * <p>Requests are validated before evaluation. If the required fields are missing,
     * a 400 Bad Request response is returned. When the request is valid, the underlying
     * rate limiter service evaluates the applicable rule and returns an allow/deny result.
     * A denied request is mapped to HTTP 429.</p>
     *
     * @param request the incoming rate-limit check request containing the client identifier
     *                and target endpoint; must not be null and must include both fields
     * @return an HTTP 200 response with the allow/deny result when the request is valid,
     *         an HTTP 400 response when validation fails, or an HTTP 429 response when the
     *         request is rejected by the configured rate limit
     */
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
