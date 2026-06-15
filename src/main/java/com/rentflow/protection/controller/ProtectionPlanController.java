package com.rentflow.protection.controller;

import com.rentflow.protection.dto.ProtectionPlanListResponse;
import com.rentflow.protection.service.ProtectionPlanService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/protection-plans")
@RequiredArgsConstructor
public class ProtectionPlanController {

    private final ProtectionPlanService protectionPlanService;

    @GetMapping
    public ResponseEntity<ProtectionPlanListResponse> listPlans() {
        return ResponseEntity.ok(protectionPlanService.listActivePlans());
    }
}
