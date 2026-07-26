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
        response.setAllowed(true);

        ConcurrentHashMap<String, LeakyBucket> leakyBucketMap =
                bucketMap.computeIfAbsent(clientId, k -> new ConcurrentHashMap<>());

        LeakyBucket bucket = leakyBucketMap.computeIfAbsent(endPoint,
                k -> new LeakyBucket(new LinkedBlockingQueue<>(bucketSize), bucketSize, false, null));

        synchronized (bucket) {
            boolean accepted = bucket.getQueue().offer(request);
            if (!accepted) {
                response.setAllowed(false);
                response.setMessage("Queue full, request dropped");
                return response;
            }

            if (!bucket.isDrainerStarted()) {
                startDrainer(bucket);
                bucket.setDrainerStarted(true);
            }

            int queueSpotsLeft = bucketSize - bucket.getQueue().size();
            response.setRemaining(queueSpotsLeft);
            response.setMessage("Request queued. Queue spots remaining: " + queueSpotsLeft);
        }

        return response;
    }

    @Override
    public String getAlgorithmName() {
        return "LEAKY_BUCKET";
    }

    private void startDrainer(LeakyBucket bucket) {
        long intervalMs = 20000L / outflowRate;

        ScheduledFuture<?> future = scheduler.scheduleAtFixedRate(
                () -> {
                    try {
                        CheckRequest request = bucket.getQueue().poll();
                        assert request != null;
                        log.info("Request processed: {}", request.getEndpoint());
                        // queue empty → stop drainer cleanly
                        if (bucket.getQueue().isEmpty()) {
                            synchronized (bucket) {
                                bucket.setDrainerStarted(false);
                            }
                            // cancel itself — clean stop, no exception
                            bucket.getDrainerFuture().cancel(false);
                        }
                    } catch (Exception e) {
                        System.err.println("Drainer error: " + e.getMessage());
                    }
                },
                intervalMs, intervalMs, TimeUnit.MILLISECONDS
        );

        // store reference in bucket so drainer can cancel itself
        bucket.setDrainerFuture(future);
    }

}
