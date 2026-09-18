package com.ratelimiter.rate_limiter_service.controller;

import com.ratelimiter.rate_limiter_service.dto.Rule;
import com.ratelimiter.rate_limiter_service.service.RuleEngineService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AdminControllerTest {

    @Test
    void createRuleReturnsCreatedRule() {
        RuleEngineService service = mock(RuleEngineService.class);
        Rule rule = new Rule("user-1", "/api/test", 5, 60, "TOKEN_BUCKET", "pro");
        when(service.addRule(any(Rule.class))).thenReturn(rule);

        AdminController controller = new AdminController(service);
        ResponseEntity<Rule> response = controller.createRule(rule);

        assertEquals(HttpStatusCode.valueOf(200), response.getStatusCode());
        assertEquals(rule.getClientId(), response.getBody().getClientId());
    }

    @Test
    void listRulesReturnsConfiguredRules() {
        RuleEngineService service = mock(RuleEngineService.class);
        List<Rule> rules = List.of(new Rule("user-1", "/api/test", 5, 60, "TOKEN_BUCKET", "pro"));
        when(service.getAllRules()).thenReturn(rules);

        AdminController controller = new AdminController(service);
        ResponseEntity<List<Rule>> response = controller.listRules();

        assertEquals(HttpStatusCode.valueOf(200), response.getStatusCode());
        assertEquals(1, response.getBody().size());
    }

    @Test
    void getRuleReturnsNotFoundForUnknownId() {
        RuleEngineService service = mock(RuleEngineService.class);
        when(service.getRuleById("missing")).thenReturn(Optional.empty());

        AdminController controller = new AdminController(service);
        ResponseEntity<Rule> response = controller.getRule("missing");

        assertEquals(HttpStatusCode.valueOf(404), response.getStatusCode());
    }

    @Test
    void updateRuleAndDeleteRuleWork() {
        RuleEngineService service = mock(RuleEngineService.class);
        Rule updated = new Rule("user-1", "/api/test", 8, 60, "LEAKY_BUCKET", "pro");
        when(service.updateRule("rule-1", updated)).thenReturn(updated);

        AdminController controller = new AdminController(service);
        ResponseEntity<Rule> updateResponse = controller.updateRule("rule-1", updated);
        ResponseEntity<Void> deleteResponse = controller.deleteRule("rule-1");

        assertEquals(HttpStatusCode.valueOf(200), updateResponse.getStatusCode());
        assertEquals(HttpStatusCode.valueOf(204), deleteResponse.getStatusCode());
    }
}
