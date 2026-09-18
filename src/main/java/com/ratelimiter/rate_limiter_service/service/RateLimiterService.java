package com.ratelimiter.rate_limiter_service.service;

import com.ratelimiter.rate_limiter_service.dto.CheckRequest;
import com.ratelimiter.rate_limiter_service.dto.CheckResponse;
import com.ratelimiter.rate_limiter_service.dto.Rule;
import com.ratelimiter.rate_limiter_service.storage.CircuitBreakerStorage;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class RateLimiterService {

    private final Map<String, RateLimiter> rateLimiterMap = new HashMap<>();
    private final RuleEngineService ruleEngineService;
    private final CircuitBreakerStorage circuitBreakerStorage;

    public RateLimiterService(List<RateLimiter> rateLimiters,
                             RuleEngineService ruleEngineService,
                             CircuitBreakerStorage circuitBreakerStorage) {
        this.ruleEngineService = ruleEngineService;
        this.circuitBreakerStorage = circuitBreakerStorage;
        for (RateLimiter limiter : rateLimiters) {
            rateLimiterMap.put(normalizeAlgorithm(limiter.getAlgorithmName()), limiter);
        }
    }

    /**
     * Checks if a request from a client to an endpoint is allowed under the rate limit.
     *
     * The method resolves the applicable rate limit rule based on clientId and endpoint
     * priority matching, selects the appropriate rate limiting algorithm, and checks the
     * request count against the limit using the configured storage backend (Redis with
     * circuit breaker fallback to in-memory storage).
     *
     * @param request the check request containing clientId and endpoint
     * @return CheckResponse with allowed flag, remaining capacity, and retry-after time
     */
    public CheckResponse check(CheckRequest request) {
        if (request == null || request.getClientId() == null || request.getClientId().isBlank()
                || request.getEndpoint() == null || request.getEndpoint().isBlank()) {
            return CheckResponse.invalidRequest("clientId and endpoint are required");
        }

        String requestedAlgorithm = request.getAlgorithm();
        Rule rule = ruleEngineService.findRule(request.getClientId(), request.getEndpoint());
        if (rule == null) {
            return CheckResponse.invalidRequest("No matching rule found for clientId and endpoint");
        }

        String algorithm = normalizeAlgorithm(
                requestedAlgorithm != null && !requestedAlgorithm.isBlank()
                        ? requestedAlgorithm
                        : rule.getAlgorithm()
        );

        RateLimiter rateLimiter = rateLimiterMap.get(algorithm);
        if (rateLimiter == null) {
            return CheckResponse.invalidRequest("Unsupported rate-limiter algorithm: " + algorithm);
        }

        String storageKey = request.getClientId() + ":" + request.getEndpoint() + ":" + algorithm;
        boolean allowed = circuitBreakerStorage.allow(storageKey, rule.getLimit());
        CheckRequest effectiveRequest = new CheckRequest(
                request.getClientId(),
                request.getEndpoint(),
                algorithm
        );

        CheckResponse response = rateLimiter.allow(effectiveRequest);
        if (!allowed) {
            response.setAllowed(false);
            response.setRetryAfter(Math.max(1L, response.getRetryAfter()));
            response.setMessage("Rate limit exceeded");
        } else {
            response.setRetryAfter(0L);
            if (response.isAllowed()) {
                response.setMessage("Request allowed");
            }
        }

        return response;
    }

    /**
     * Verifies whether the provided algorithm name is supported by this service.
     *
     * @param algorithm the algorithm name to check, such as "TOKEN_BUCKET" or "FIXED_WINDOW"
     * @return true if the algorithm is implemented and available, false otherwise
     */
    public boolean supportsAlgorithm(String algorithm) {
        return algorithm != null
                && !algorithm.isBlank()
                && rateLimiterMap.containsKey(normalizeAlgorithm(algorithm));
    }

    private String normalizeAlgorithm(String algorithm) {
        if (algorithm == null) {
            return "";
        }
        String normalized = algorithm.trim().toUpperCase(Locale.ROOT);
        if (normalized.equals("SLIDING_WINDOW")) {
            return "SLIDING_WINDOW_COUNTER";
        }
        return normalized;
    }
}