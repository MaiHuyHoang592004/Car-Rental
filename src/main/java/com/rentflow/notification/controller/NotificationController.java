package com.rentflow.notification.controller;

import com.rentflow.common.security.SecurityContext;
import com.rentflow.common.web.PageResponse;
import com.rentflow.common.web.PageableValidation;
import com.rentflow.notification.dto.NotificationResponse;
import com.rentflow.notification.service.NotificationService;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationService notificationService;
    private final SecurityContext securityContext;

    public NotificationController(NotificationService notificationService, SecurityContext securityContext) {
        this.notificationService = notificationService;
        this.securityContext = securityContext;
    }

    @GetMapping("/me")
    public ResponseEntity<PageResponse<NotificationResponse>> listMyNotifications(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageableValidation.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return ResponseEntity.ok(PageResponse.from(
                notificationService.listMyNotifications(securityContext.currentUserId(), pageable)));
    }

    @GetMapping("/me/unread-count")
    public ResponseEntity<NotificationUnreadCountResponse> unreadCount() {
        return ResponseEntity.ok(new NotificationUnreadCountResponse(
                notificationService.countUnread(securityContext.currentUserId())));
    }

    @PostMapping("/{notificationId}/read")
    public ResponseEntity<NotificationResponse> markRead(@PathVariable UUID notificationId) {
        return ResponseEntity.ok(notificationService.markRead(securityContext.currentUserId(), notificationId));
    }

    @PostMapping("/me/read-all")
    public ResponseEntity<NotificationReadAllResponse> markAllRead() {
        return ResponseEntity.ok(new NotificationReadAllResponse(
                notificationService.markAllRead(securityContext.currentUserId())));
    }

    public record NotificationUnreadCountResponse(long unreadCount) {
    }

    public record NotificationReadAllResponse(long updatedCount) {
    }
}
