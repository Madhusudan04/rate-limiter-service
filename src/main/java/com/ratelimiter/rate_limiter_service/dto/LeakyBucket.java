package com.ratelimiter.rate_limiter_service.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledFuture;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LeakyBucket {
    private LinkedBlockingQueue<CheckRequest> queue;
    private int bucketSize;
    private boolean drainerStarted;
    private ScheduledFuture<?> drainerFuture;
}
