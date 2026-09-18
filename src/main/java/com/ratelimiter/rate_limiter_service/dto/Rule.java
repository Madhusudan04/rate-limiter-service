package com.ratelimiter.rate_limiter_service.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Rule {
    private String id;
    private String clientId;
    private String endpoint;
    @Min(1)
    private int limit;
    @Min(1)
    private int windowSeconds;
    @NotBlank
    private String algorithm;
    private String tier;

    public Rule(String clientId, String endpoint, int limit, int windowSeconds, String algorithm, String tier) {
        this.id = UUID.randomUUID().toString();
        this.clientId = clientId;
        this.endpoint = endpoint;
        this.limit = limit;
        this.windowSeconds = windowSeconds;
        this.algorithm = algorithm;
        this.tier = tier;
    }
}
