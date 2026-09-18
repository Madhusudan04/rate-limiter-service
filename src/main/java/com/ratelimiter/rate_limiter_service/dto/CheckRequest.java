package com.ratelimiter.rate_limiter_service.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CheckRequest {

    @NotBlank
    private String clientId;
    @NotBlank
    private String endpoint;
    private String algorithm;

}
