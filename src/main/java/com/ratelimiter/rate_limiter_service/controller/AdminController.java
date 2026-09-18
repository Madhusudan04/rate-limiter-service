package com.ratelimiter.rate_limiter_service.controller;

import com.ratelimiter.rate_limiter_service.dto.Rule;
import com.ratelimiter.rate_limiter_service.service.RuleEngineService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Administrative controller for managing dynamic rate-limit rules.
 *
 * <p>This controller exposes endpoints to create, list, fetch, update, and delete rules
 * that determine which algorithm and limit apply to a given client and endpoint.</p>
 */
@RestController
@RequestMapping("/admin")
public class AdminController {

    private final RuleEngineService ruleEngineService;

    public AdminController(RuleEngineService ruleEngineService) {
        this.ruleEngineService = ruleEngineService;
    }

    /**
     * Creates and registers a new rate-limit rule.
     *
     * @param rule the rule configuration to persist and apply for matching requests
     * @return the created rule as a persisted entity with the assigned configuration
     */
    @PostMapping("/rules")
    public ResponseEntity<Rule> createRule(@Valid @RequestBody Rule rule) {
        return ResponseEntity.ok(ruleEngineService.addRule(rule));
    }

    /**
     * Returns all configured rate-limit rules.
     *
     * @return a list of all active rules in the current rule engine
     */
    @GetMapping("/rules")
    public ResponseEntity<List<Rule>> listRules() {
        return ResponseEntity.ok(ruleEngineService.getAllRules());
    }

    /**
     * Fetches a rule by its unique identifier.
     *
     * @param id the rule identifier to look up
     * @return the matching rule wrapped in an HTTP 200 response, or HTTP 404 when absent
     */
    @GetMapping("/rules/{id}")
    public ResponseEntity<Rule> getRule(@PathVariable String id) {
        return ruleEngineService.getRuleById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Updates an existing rule definition.
     *
     * @param id the identifier of the rule to update
     * @param rule the updated rule payload
     * @return the updated rule after persistence
     */
    @PutMapping("/rules/{id}")
    public ResponseEntity<Rule> updateRule(@PathVariable String id, @Valid @RequestBody Rule rule) {
        return ResponseEntity.ok(ruleEngineService.updateRule(id, rule));
    }

    /**
     * Deletes a rule by identifier.
     *
     * @param id the rule identifier to remove
     * @return HTTP 204 response when the delete operation completes successfully
     */
    @DeleteMapping("/rules/{id}")
    public ResponseEntity<Void> deleteRule(@PathVariable String id) {
        ruleEngineService.deleteRule(id);
        return ResponseEntity.noContent().build();
    }
}
