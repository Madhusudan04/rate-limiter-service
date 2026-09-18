package com.ratelimiter.rate_limiter_service.service;

import com.ratelimiter.rate_limiter_service.dto.Rule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RuleEngineServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void priorityMatchingPrefersSpecificRules() {
        RuleEngineService service = new RuleEngineService(tempDir.resolve("rules.json").toString());
        service.initialize();

        service.addRule(new Rule("user-1", "/api/pay", 10, 60, "TOKEN_BUCKET", "pro"));
        service.addRule(new Rule("user-1", "*", 20, 60, "FIXED_WINDOW", "pro"));
        service.addRule(new Rule("*", "/api/pay", 30, 60, "LEAKY_BUCKET", "default"));

        Rule resolved = service.findRule("user-1", "/api/pay");

        assertEquals("user-1", resolved.getClientId());
        assertEquals("/api/pay", resolved.getEndpoint());
        assertEquals(10, resolved.getLimit());
    }

    @Test
    void matchingIsCaseInsensitive() {
        RuleEngineService service = new RuleEngineService(tempDir.resolve("rules.json").toString());
        service.initialize();

        service.addRule(new Rule("User-123", "/Api/Pay", 7, 60, "TOKEN_BUCKET", "pro"));

        Rule resolved = service.findRule("user-123", "/api/pay");

        assertEquals("User-123", resolved.getClientId());
        assertEquals("/Api/Pay", resolved.getEndpoint());
        assertEquals(7, resolved.getLimit());
    }

    @Test
    void initializeCreatesEmptyRulesFileWhenMissing() throws Exception {
        Path rulesFile = tempDir.resolve("missing-rules.json");
        RuleEngineService service = new RuleEngineService(rulesFile.toString());

        assertFalse(Files.exists(rulesFile));
        service.initialize();

        assertTrue(Files.exists(rulesFile));
        assertTrue(Files.size(rulesFile) > 0);
    }

    @Test
    void wildcardRuleFallsBackWhenNoExactMatch() {
        RuleEngineService service = new RuleEngineService(tempDir.resolve("rules.json").toString());
        service.initialize();

        Rule resolved = service.findRule("unknown-user", "/api/pay");

        assertNotNull(resolved);
        assertEquals("*", resolved.getClientId());
    }

    @Test
    void addRulePersistsAndReturnsRule() {
        RuleEngineService service = new RuleEngineService(tempDir.resolve("rules.json").toString());
        service.initialize();

        Rule rule = new Rule("user-2", "/api/checkout", 5, 60, "TOKEN_BUCKET", "pro");
        Rule saved = service.addRule(rule);

        assertEquals(rule.getClientId(), saved.getClientId());
        assertNotNull(service.getRuleById(saved.getId()));
    }

    @Test
    void updateRuleReplacesExistingRecord() {
        RuleEngineService service = new RuleEngineService(tempDir.resolve("rules.json").toString());
        service.initialize();

        Rule original = service.addRule(new Rule("user-3", "/api/cart", 3, 60, "FIXED_WINDOW", "pro"));
        Rule updated = new Rule("user-3", "/api/cart", 9, 30, "TOKEN_BUCKET", "premium");

        Rule result = service.updateRule(original.getId(), updated);

        assertEquals(9, result.getLimit());
        assertEquals(30, result.getWindowSeconds());
    }

    @Test
    void deleteRuleRemovesItFromRegistry() {
        RuleEngineService service = new RuleEngineService(tempDir.resolve("rules.json").toString());
        service.initialize();

        Rule created = service.addRule(new Rule("user-4", "/api/profile", 7, 60, "LEAKY_BUCKET", "basic"));
        service.deleteRule(created.getId());

        List<Rule> rules = service.getAllRules();
        assertTrue(rules.stream().noneMatch(rule -> rule.getId().equals(created.getId())));
    }

    @Test
    void updateRuleThrowsWhenIdDoesNotExist() {
        RuleEngineService service = new RuleEngineService(tempDir.resolve("rules.json").toString());
        service.initialize();

        Rule updated = new Rule("user-5", "/api/nope", 3, 60, "FIXED_WINDOW", "basic");

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> service.updateRule("missing-id", updated));

        assertTrue(exception.getMessage().contains("Rule not found"));
    }
}
