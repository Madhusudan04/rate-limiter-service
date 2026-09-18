package com.ratelimiter.rate_limiter_service.service.impl;

import com.ratelimiter.rate_limiter_service.dto.CheckRequest;
import com.ratelimiter.rate_limiter_service.dto.CheckResponse;
import com.ratelimiter.rate_limiter_service.dto.LeakyBucket;
import com.ratelimiter.rate_limiter_service.service.RateLimiter;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Service
public class LeakyBucketRateLimiter implements RateLimiter {

    private final ConcurrentHashMap<String, ConcurrentHashMap<String, LeakyBucket>> bucketMap = new ConcurrentHashMap<>();
    private ScheduledExecutorService scheduler;

    @Value("${leakyBucketSize:4}")
    private int bucketSize;

    @Value("${outflowRate:2}")
    private int outflowRate;

    @Value("${schedulerPoolSize:10}")
    private int schedulerPoolSize;

    @PostConstruct
    public void init(){
        if (bucketSize <= 0 || outflowRate <= 0 || outflowRate > 1000 || schedulerPoolSize <= 0) {
            throw new IllegalStateException(
                    "leakyBucketSize, outflowRate, and schedulerPoolSize must be positive; "
                            + "outflowRate must not exceed 1000");
        }
        scheduler = Executors.newScheduledThreadPool(schedulerPoolSize);
    }

    @PreDestroy
    public void shutdown() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException ignored) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public CheckResponse allow(CheckRequest request) {
        String clientId = request.getClientId();
        String endPoint = request.getEndpoint();

        CheckResponse response = new CheckResponse();

        ConcurrentHashMap<String, LeakyBucket> leakyBucketMap =
                bucketMap.computeIfAbsent(clientId, k -> new ConcurrentHashMap<>());

        LeakyBucket bucket = leakyBucketMap.computeIfAbsent(endPoint,
                k -> new LeakyBucket(new LinkedBlockingQueue<>(bucketSize), bucketSize, false, null));

        synchronized (bucket) {
            boolean accepted = bucket.getQueue().offer(request);

            if (!accepted) {
                long intervalMs = 1000L / outflowRate;
                int retryAfterSeconds = (int) Math.ceil(intervalMs / 1000.0);

                response.setAllowed(false);
                response.setRemaining(0);
                response.setRetryAfter(Math.max(1, retryAfterSeconds));
                response.setMessage("Queue full, request dropped");
                return response;
            }

            if (!bucket.isDrainerStarted()) {
                startDrainer(bucket);
                bucket.setDrainerStarted(true);
            }

            response.setAllowed(true);
            int queueSpotsLeft = bucketSize - bucket.getQueue().size();
            response.setRemaining(queueSpotsLeft);
            response.setRetryAfter(0);
            response.setMessage("Request queued. Queue spots remaining: " + queueSpotsLeft);
        }

        return response;
    }

    @Override
    public String getAlgorithmName() {
        return "LEAKY_BUCKET";
    }

    private void startDrainer(LeakyBucket bucket) {
        long intervalMs = 1000L / outflowRate;
        AtomicReference<ScheduledFuture<?>> futureReference = new AtomicReference<>();

        ScheduledFuture<?> future = scheduler.scheduleAtFixedRate(
                () -> {
                    try {
                        ScheduledFuture<?> currentFuture = futureReference.get();
                        synchronized (bucket) {
                            if (bucket.getDrainerFuture() != currentFuture) {
                                return;
                            }

                            CheckRequest request = bucket.getQueue().poll();
                            if (request == null) {
                                bucket.setDrainerStarted(false);
                                bucket.setDrainerFuture(null);
                                currentFuture.cancel(false);
                                return;
                            }

                            log.info("Request processed: clientId={}, endpoint={}",
                                    request.getClientId(), request.getEndpoint());

                            if (bucket.getQueue().isEmpty()) {
                                bucket.setDrainerStarted(false);
                                bucket.setDrainerFuture(null);
                                currentFuture.cancel(false);
                            }
                        }
                    } catch (RuntimeException e) {
                        log.error("Leaky bucket drainer failed", e);
                    }
                },
                intervalMs, intervalMs, TimeUnit.MILLISECONDS
        );

        futureReference.set(future);
        bucket.setDrainerFuture(future);
    }
}
