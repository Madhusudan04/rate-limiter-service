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

@RestController
@RequestMapping("/admin")
public class AdminController {

    private final RuleEngineService ruleEngineService;

    public AdminController(RuleEngineService ruleEngineService) {
        this.ruleEngineService = ruleEngineService;
    }

    @PostMapping("/rules")
    public ResponseEntity<Rule> createRule(@Valid @RequestBody Rule rule) {
        return ResponseEntity.ok(ruleEngineService.addRule(rule));
    }

    @GetMapping("/rules")
    public ResponseEntity<List<Rule>> listRules() {
        return ResponseEntity.ok(ruleEngineService.getAllRules());
    }

    @GetMapping("/rules/{id}")
    public ResponseEntity<Rule> getRule(@PathVariable String id) {
        return ruleEngineService.getRuleById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/rules/{id}")
    public ResponseEntity<Rule> updateRule(@PathVariable String id, @Valid @RequestBody Rule rule) {
        return ResponseEntity.ok(ruleEngineService.updateRule(id, rule));
    }

    @DeleteMapping("/rules/{id}")
    public ResponseEntity<Void> deleteRule(@PathVariable String id) {
        ruleEngineService.deleteRule(id);
        return ResponseEntity.noContent().build();
    }
}
