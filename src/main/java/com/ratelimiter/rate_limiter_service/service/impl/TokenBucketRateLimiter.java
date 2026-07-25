package com.ratelimiter.rate_limiter_service.service.impl;

import com.ratelimiter.rate_limiter_service.dto.CheckRequest;
import com.ratelimiter.rate_limiter_service.dto.CheckResponse;
import com.ratelimiter.rate_limiter_service.dto.TokenBucket;
import com.ratelimiter.rate_limiter_service.service.RateLimiterService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class TokenBucketRateLimiter implements RateLimiterService {

    private final Map<String, Map<String, TokenBucket>> tokenBucketMap = new ConcurrentHashMap<>();

    @Value("${maxTokensForTokenBucket:10}")
    private double maxTokens;

    @Value("${windowSeconds:60}")
    private int windowSeconds;

    @Override
    public CheckResponse allow(CheckRequest request) {
        Instant now = Instant.now();
        String endPoint = request.getEndpoint();
        String clientId = request.getClientId();

        CheckResponse response = new CheckResponse();
        response.setAllowed(true);

        Map<String, TokenBucket> endpointMap =
                tokenBucketMap.computeIfAbsent(clientId, k -> new ConcurrentHashMap<>());

        TokenBucket tokenBucket = endpointMap.computeIfAbsent(endPoint,
                k -> new TokenBucket(maxTokens, now));

        synchronized (tokenBucket) {
            long elapsedMillis = Duration.between(tokenBucket.getLastRefillTime(), now).toMillis();
            if (elapsedMillis < 0) {
                elapsedMillis = 0;
            }

            if (elapsedMillis > 0) {
                double refillRatePerMillis = maxTokens / (windowSeconds * 1000.0);
                double tokensToAdd = elapsedMillis * refillRatePerMillis;
                tokenBucket.setToken(Math.min(maxTokens, tokenBucket.getToken() + tokensToAdd));
                tokenBucket.setLastRefillTime(now);
            }

            double availableTokens = tokenBucket.getToken();
            if (availableTokens < 1.0) {
                response.setAllowed(false);
                response.setRemaining(Math.max(0.0, availableTokens));

                double refillRatePerSecond = maxTokens / windowSeconds;
                double missingTokens = 1.0 - availableTokens;
                int retryAfter = (int) Math.ceil(missingTokens / refillRatePerSecond);
                response.setRetryAfter(Math.max(0, retryAfter));
            } else {
                tokenBucket.setToken(availableTokens - 1.0);
                if (tokenBucket.getToken() < 1e-9) {
                    tokenBucket.setToken(0.0);
                }
                response.setRemaining(tokenBucket.getToken());
            }
        }

        return response;
    }
}