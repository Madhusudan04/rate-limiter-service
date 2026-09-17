package com.ratelimiter.rate_limiter_service.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CheckResponse {

    private boolean allowed;
    private double remaining;
    private long retryAfter;
    private String message;

}
