package com.ratelimiter.rate_limiter_service.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ratelimiter.rate_limiter_service.dto.Rule;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class RuleEngineService {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Path storagePath;
    private final List<Rule> rules = new CopyOnWriteArrayList<>();

    public RuleEngineService(@Value("${rule.engine.storage.file:rules.json}") String storageFile) {
        this.storagePath = Path.of(storageFile);
    }

    @PostConstruct
    public void initialize() {
        loadRules();
        if (rules.isEmpty()) {
            seedDefaultRules();
            persistRules();
        }
    }

    public Rule addRule(Rule rule) {
        Rule normalized = normalizeRule(rule);
        rules.removeIf(existing -> Objects.equals(existing.getId(), normalized.getId()));
        rules.add(normalized);
        persistRules();
        return normalized;
    }

    public List<Rule> getAllRules() {
        return new ArrayList<>(rules);
    }

    public Optional<Rule> getRuleById(String id) {
        return rules.stream().filter(rule -> Objects.equals(rule.getId(), id)).findFirst();
    }

    public Rule findRule(String clientId, String endpoint) {
        String normalizedClient = normalizeValue(clientId);
        String normalizedEndpoint = normalizeValue(endpoint);

        Optional<Rule> exact = rules.stream()
                .filter(rule -> isRuleMatch(rule, normalizedClient, normalizedEndpoint, true))
                .findFirst();
        if (exact.isPresent()) {
            return exact.get();
        }

        Optional<Rule> clientOnly = rules.stream()
                .filter(rule -> isRuleMatch(rule, normalizedClient, normalizedEndpoint, false))
                .filter(rule -> rule.getEndpoint() == null || rule.getEndpoint().isBlank() || "*".equals(rule.getEndpoint()))
                .findFirst();
        if (clientOnly.isPresent()) {
            return clientOnly.get();
        }

        Optional<Rule> endpointOnly = rules.stream()
                .filter(rule -> rule.getClientId() == null || rule.getClientId().isBlank() || "*".equals(rule.getClientId()))
                .filter(rule -> isEndpointMatch(rule.getEndpoint(), normalizedEndpoint))
                .findFirst();
        if (endpointOnly.isPresent()) {
            return endpointOnly.get();
        }

        return rules.stream()
                .filter(rule -> isDefaultRule(rule))
                .findFirst()
                .orElseGet(() -> new Rule("*", "*", 50, 60, "TOKEN_BUCKET", "default"));
    }

    public void deleteRule(String id) {
        rules.removeIf(rule -> Objects.equals(rule.getId(), id));
        persistRules();
    }

    public Rule updateRule(String id, Rule updatedRule) {
        Rule normalized = normalizeRule(updatedRule);
        normalized.setId(id);
        for (int i = 0; i < rules.size(); i++) {
            if (Objects.equals(rules.get(i).getId(), id)) {
                rules.set(i, normalized);
                persistRules();
                return normalized;
            }
        }
        throw new IllegalArgumentException("Rule not found: " + id);
    }

    public void loadRules() {
        try {
            if (Files.notExists(storagePath)) {
                return;
            }
            String content = Files.readString(storagePath);
            if (content == null || content.isBlank()) {
                return;
            }
            List<Rule> loaded = objectMapper.readValue(content, new TypeReference<>() {});
            rules.clear();
            rules.addAll(loaded);
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to load rules from " + storagePath, ex);
        }
    }

    public void persistRules() {
        try {
            Files.createDirectories(storagePath.getParent() == null ? Path.of(".") : storagePath.getParent());
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(storagePath.toFile(), rules);
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to store rules to " + storagePath, ex);
        }
    }

    private void seedDefaultRules() {
        rules.clear();
        rules.add(new Rule(UUID.randomUUID().toString(), "user-123", "/api/checkout", 5, 60, "TOKEN_BUCKET", "pro"));
        rules.add(new Rule(UUID.randomUUID().toString(), "user-123", "/api/*", 100, 60, "SLIDING_WINDOW_COUNTER", "pro"));
        rules.add(new Rule(UUID.randomUUID().toString(), "*", "/api/*", 50, 60, "TOKEN_BUCKET", "default"));
        rules.add(new Rule(UUID.randomUUID().toString(), "*", "*", 50, 60, "TOKEN_BUCKET", "default"));
    }

    private Rule normalizeRule(Rule rule) {
        if (rule == null) {
            throw new IllegalArgumentException("Rule cannot be null");
        }
        if (rule.getId() == null || rule.getId().isBlank()) {
            rule.setId(UUID.randomUUID().toString());
        }
        if (rule.getAlgorithm() == null || rule.getAlgorithm().isBlank()) {
            rule.setAlgorithm("TOKEN_BUCKET");
        }
        if (rule.getLimit() <= 0) {
            throw new IllegalArgumentException("Rule limit must be positive");
        }
        if (rule.getWindowSeconds() <= 0) {
            throw new IllegalArgumentException("Rule windowSeconds must be positive");
        }
        return rule;
    }

    private boolean isDefaultRule(Rule rule) {
        return (rule.getClientId() == null || rule.getClientId().isBlank() || "*".equals(rule.getClientId()))
                && (rule.getEndpoint() == null || rule.getEndpoint().isBlank() || "*".equals(rule.getEndpoint()));
    }

    private boolean isRuleMatch(Rule rule, String clientId, String endpoint, boolean requireExactEndpoint) {
        if (rule == null) {
            return false;
        }

        boolean clientMatches = matchesPattern(rule.getClientId(), clientId, true);
        boolean endpointMatches = matchesPattern(rule.getEndpoint(), endpoint, requireExactEndpoint);
        return clientMatches && endpointMatches;
    }

    private boolean matchesPattern(String pattern, String value, boolean exact) {
        if (pattern == null || pattern.isBlank() || "*".equals(pattern)) {
            return true;
        }
        if (value == null || value.isBlank()) {
            return false;
        }
        if (exact) {
            return pattern.equals(value);
        }
        return isEndpointMatch(pattern, value);
    }

    private boolean isEndpointMatch(String pattern, String endpoint) {
        if (pattern == null || pattern.isBlank() || "*".equals(pattern)) {
            return true;
        }
        if (endpoint == null || endpoint.isBlank()) {
            return false;
        }
        if (pattern.endsWith("/*")) {
            String prefix = pattern.substring(0, pattern.length() - 1);
            return endpoint.startsWith(prefix);
        }
        return pattern.equals(endpoint);
    }

    private String normalizeValue(String value) {
        return value == null ? "" : value.trim();
    }
}
