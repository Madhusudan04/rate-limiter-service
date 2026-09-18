package com.ratelimiter.rate_limiter_service.controller;

import com.ratelimiter.rate_limiter_service.dto.CheckRequest;
import com.ratelimiter.rate_limiter_service.dto.CheckResponse;
import com.ratelimiter.rate_limiter_service.service.RateLimiterService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RateLimitControllerTest {

    @Test
    void validRequestReturnsOkResponse() {
        RateLimiterService service = mock(RateLimiterService.class);
        when(service.check(any(CheckRequest.class))).thenReturn(new CheckResponse(true, 2, 0, "Request allowed"));

        RateLimitController controller = new RateLimitController(service);
        ResponseEntity<CheckResponse> response = controller.rateLimiter(new CheckRequest("user-1", "/api/test", "TOKEN_BUCKET"));

        assertEquals(HttpStatusCode.valueOf(200), response.getStatusCode());
        assertTrue(response.getBody().isAllowed());
    }

    @Test
    void missingClientIdReturnsBadRequest() {
        RateLimiterService service = mock(RateLimiterService.class);
        RateLimitController controller = new RateLimitController(service);

        ResponseEntity<CheckResponse> response = controller.rateLimiter(new CheckRequest("", "/api/test", "TOKEN_BUCKET"));

        assertEquals(HttpStatusCode.valueOf(400), response.getStatusCode());
        assertFalse(response.getBody().isAllowed());
    }

    @Test
    void blockedRequestReturnsTooManyRequests() {
        RateLimiterService service = mock(RateLimiterService.class);
        when(service.check(any(CheckRequest.class))).thenReturn(new CheckResponse(false, 0, 5, "Rate limit exceeded"));

        RateLimitController controller = new RateLimitController(service);
        ResponseEntity<CheckResponse> response = controller.rateLimiter(new CheckRequest("user-1", "/api/test", "TOKEN_BUCKET"));

        assertEquals(HttpStatusCode.valueOf(429), response.getStatusCode());
        assertFalse(response.getBody().isAllowed());
    }

    @Test
    void allowedRequestReturnsHttp200() {
        RateLimiterService service = mock(RateLimiterService.class);
        when(service.check(any(CheckRequest.class))).thenReturn(new CheckResponse(true, 1, 0, "Request allowed"));

        RateLimitController controller = new RateLimitController(service);
        ResponseEntity<CheckResponse> response = controller.rateLimiter(new CheckRequest("user-1", "/api/test", "TOKEN_BUCKET"));

        assertEquals(HttpStatusCode.valueOf(200), response.getStatusCode());
        assertTrue(response.getBody().isAllowed());
    }
}
