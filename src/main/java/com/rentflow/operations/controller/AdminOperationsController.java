package com.rentflow.operations.controller;

import com.rentflow.operations.dto.AdminOperationsQueueResponse;
import com.rentflow.operations.service.AdminOperationsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/operations")
@RequiredArgsConstructor
public class AdminOperationsController {

    private final AdminOperationsService adminOperationsService;

    @GetMapping("/queues")
    public ResponseEntity<AdminOperationsQueueResponse> queueCounts() {
        return ResponseEntity.ok(adminOperationsService.getQueueCounts());
    }
}
