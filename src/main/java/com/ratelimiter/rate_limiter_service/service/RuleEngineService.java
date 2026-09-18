package com.ratelimiter.rate_limiter_service.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ratelimiter.rate_limiter_service.dto.Rule;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
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
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@Service
public class RuleEngineService {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Path storagePath;
    private final List<Rule> rules = new CopyOnWriteArrayList<>();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private volatile ScheduledFuture<?> pendingWrite;

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
        schedulePersist();
        return normalized;
    }

    public List<Rule> getAllRules() {
        return new ArrayList<>(rules);
    }

    public Optional<Rule> getRuleById(String id) {
        return rules.stream().filter(rule -> Objects.equals(rule.getId(), id)).findFirst();
    }

/**
     * Finds the most specific matching rule for a given client and endpoint.
     *
     * Rule priority is evaluated from most specific to least specific:
     * 1. clientId + endpoint exact match
     * 2. clientId only match
     * 3. endpoint only match
     * 4. default wildcard rule
     *
     * @param clientId the client identifier, such as "user-123"
     * @param endpoint the endpoint path, such as "/api/checkout"
     * @return the matching Rule, falling back to the default rule when necessary
     */
    public Rule findRule(String clientId, String endpoint) {
        String normalizedClient = normalizeValue(clientId);
        String normalizedEndpoint = normalizeValue(endpoint);

        Optional<Rule> exact = rules.stream()
                .filter(rule -> isExactClientEndpointMatch(rule, normalizedClient, normalizedEndpoint))
                .findFirst();
        if (exact.isPresent()) {
            return exact.get();
        }

        Optional<Rule> clientOnly = rules.stream()
                .filter(rule -> isClientLevelMatch(rule, normalizedClient, normalizedEndpoint))
                .findFirst();
        if (clientOnly.isPresent()) {
            return clientOnly.get();
        }

        Optional<Rule> endpointOnly = rules.stream()
                .filter(rule -> isEndpointLevelMatch(rule, normalizedClient, normalizedEndpoint))
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
        schedulePersist();
    }

    public Rule updateRule(String id, Rule updatedRule) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Rule id must not be blank");
        }
        if (updatedRule == null) {
            throw new IllegalArgumentException("Rule cannot be null");
        }

        boolean found = false;
        Rule normalized = normalizeRule(updatedRule);
        normalized.setId(id);
        for (int i = 0; i < rules.size(); i++) {
            if (Objects.equals(rules.get(i).getId(), id)) {
                rules.set(i, normalized);
                found = true;
                break;
            }
        }
        if (!found) {
            throw new IllegalArgumentException("Rule not found: " + id);
        }
        schedulePersist();
        return normalized;
    }

    public void loadRules() {
        try {
            if (Files.notExists(storagePath)) {
                Files.createDirectories(storagePath.getParent() == null ? Path.of(".") : storagePath.getParent());
                Files.createFile(storagePath);
                objectMapper.writerWithDefaultPrettyPrinter().writeValue(storagePath.toFile(), new ArrayList<Rule>());
                return;
            }
            String content = Files.readString(storagePath);
            if (content == null || content.isBlank()) {
                objectMapper.writerWithDefaultPrettyPrinter().writeValue(storagePath.toFile(), new ArrayList<Rule>());
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

    private void schedulePersist() {
        if (pendingWrite != null) {
            pendingWrite.cancel(false);
        }
        pendingWrite = scheduler.schedule(this::persistRules, 5, TimeUnit.SECONDS);
    }

    @PreDestroy
    public void shutdown() {
        if (pendingWrite != null) {
            pendingWrite.cancel(false);
        }
        persistRules();
        scheduler.shutdown();
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
        return (rule.getClientId() == null || rule.getClientId().isBlank() || "*".equalsIgnoreCase(rule.getClientId()))
                && (rule.getEndpoint() == null || rule.getEndpoint().isBlank() || "*".equalsIgnoreCase(rule.getEndpoint()));
    }

    private boolean isExactClientEndpointMatch(Rule rule, String clientId, String endpoint) {
        if (rule == null) {
            return false;
        }
        return matchesExact(rule.getClientId(), clientId)
                && matchesExact(rule.getEndpoint(), endpoint);
    }

    private boolean isClientLevelMatch(Rule rule, String clientId, String endpoint) {
        if (rule == null) {
            return false;
        }
        return matchesExact(rule.getClientId(), clientId)
                && isWildcard(rule.getEndpoint())
                && !isDefaultRule(rule);
    }

    private boolean isEndpointLevelMatch(Rule rule, String clientId, String endpoint) {
        if (rule == null) {
            return false;
        }
        return isWildcard(rule.getClientId())
                && matchesEndpoint(rule.getEndpoint(), endpoint)
                && !isDefaultRule(rule);
    }

    private boolean matchesExact(String pattern, String value) {
        if (pattern == null || pattern.isBlank()) {
            return value == null || value.isBlank();
        }
        if ("*".equalsIgnoreCase(pattern)) {
            return false;
        }
        return value != null && !value.isBlank() && pattern.equalsIgnoreCase(value);
    }

    private boolean matchesEndpoint(String pattern, String endpoint) {
        if (pattern == null || pattern.isBlank() || "*".equalsIgnoreCase(pattern)) {
            return true;
        }
        if (endpoint == null || endpoint.isBlank()) {
            return false;
        }
        String normalizedPattern = pattern.trim();
        String normalizedEndpoint = endpoint.trim();
        if (normalizedPattern.endsWith("/*")) {
            String prefix = normalizedPattern.substring(0, normalizedPattern.length() - 1);
            return normalizedEndpoint.regionMatches(true, 0, prefix, 0, prefix.length());
        }
        return normalizedPattern.equalsIgnoreCase(normalizedEndpoint);
    }

    private boolean isWildcard(String value) {
        return value == null || value.isBlank() || "*".equalsIgnoreCase(value);
    }

    private String normalizeValue(String value) {
        return value == null ? "" : value.trim();
    }
}
