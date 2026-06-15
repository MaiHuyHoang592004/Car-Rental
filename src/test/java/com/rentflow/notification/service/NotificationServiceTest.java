package com.rentflow.notification.service;

import com.rentflow.notification.dto.NotificationResponse;
import com.rentflow.common.exception.ResourceNotFoundException;
import com.rentflow.notification.entity.Notification;
import com.rentflow.notification.entity.NotificationDeliveryStatus;
import com.rentflow.notification.entity.NotificationType;
import com.rentflow.notification.repository.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    private NotificationService notificationService;

    @BeforeEach
    void setUp() {
        notificationService = new NotificationService(
                notificationRepository,
                Clock.fixed(Instant.parse("2026-06-07T00:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void createSetsSentDeliveryStatusAndReturnsResponse() {
        UUID userId = UUID.fromString("11111111-1111-4111-8111-111111111111");
        when(notificationRepository.save(any(Notification.class))).thenAnswer(invocation -> {
            Notification notification = invocation.getArgument(0);
            notification.setId(UUID.fromString("22222222-2222-4222-8222-222222222222"));
            notification.setCreatedAt(Instant.parse("2026-05-28T00:00:00Z"));
            return notification;
        });

        NotificationResponse response = notificationService.create(
                userId,
                NotificationType.DRIVER_VERIFICATION_EXPIRED,
                "Driver License Expired",
                "Your driver license has expired. Please submit a new verification.");

        assertThat(response.id()).isEqualTo(UUID.fromString("22222222-2222-4222-8222-222222222222"));
        assertThat(response.type()).isEqualTo("DRIVER_VERIFICATION_EXPIRED");
        assertThat(response.title()).isEqualTo("Driver License Expired");

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(userId);
        assertThat(captor.getValue().getDeliveryStatus()).isEqualTo(NotificationDeliveryStatus.SENT);
    }

    @Test
    void listMyNotificationsMapsPageInCreatedAtOrder() {
        UUID userId = UUID.fromString("aaaaaaaa-aaaa-4aaa-aaaa-aaaaaaaaaaaa");
        Notification newest = notification(userId, "New", Instant.parse("2026-05-28T00:00:00Z"));
        Notification older = notification(userId, "Old", Instant.parse("2026-05-27T00:00:00Z"));
        PageRequest pageable = PageRequest.of(0, 20);
        when(notificationRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable))
                .thenReturn(new PageImpl<>(List.of(newest, older), pageable, 2));

        var page = notificationService.listMyNotifications(userId, pageable);

        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getContent()).extracting(NotificationResponse::title)
                .containsExactly("New", "Old");
    }

    @Test
    void markReadSetsReadAtOnce() {
        UUID userId = UUID.fromString("aaaaaaaa-aaaa-4aaa-aaaa-aaaaaaaaaaaa");
        Notification notification = notification(userId, "New", Instant.parse("2026-05-28T00:00:00Z"));
        when(notificationRepository.findByIdAndUserId(notification.getId(), userId)).thenReturn(Optional.of(notification));
        when(notificationRepository.save(any(Notification.class))).thenAnswer(invocation -> invocation.getArgument(0));

        NotificationResponse response = notificationService.markRead(userId, notification.getId());

        assertThat(response.readAt()).isEqualTo(Instant.parse("2026-06-07T00:00:00Z"));
        verify(notificationRepository).save(notification);
    }

    @Test
    void markReadRejectsNotificationsOwnedByAnotherUser() {
        UUID userId = UUID.fromString("aaaaaaaa-aaaa-4aaa-aaaa-aaaaaaaaaaaa");
        UUID notificationId = UUID.fromString("bbbbbbbb-bbbb-4bbb-bbbb-bbbbbbbbbbbb");
        when(notificationRepository.findByIdAndUserId(notificationId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> notificationService.markRead(userId, notificationId))
                .isInstanceOf(ResourceNotFoundException.class)
                .extracting("code")
                .isEqualTo("NOTIFICATION_NOT_FOUND");

        verify(notificationRepository, never()).save(any(Notification.class));
    }

    @Test
    void countUnreadDelegatesToRepository() {
        UUID userId = UUID.fromString("aaaaaaaa-aaaa-4aaa-aaaa-aaaaaaaaaaaa");
        when(notificationRepository.countByUserIdAndReadAtIsNull(userId)).thenReturn(3L);

        assertThat(notificationService.countUnread(userId)).isEqualTo(3L);
    }

    @Test
    void markAllReadDelegatesToBulkUpdate() {
        UUID userId = UUID.fromString("aaaaaaaa-aaaa-4aaa-aaaa-aaaaaaaaaaaa");
        when(notificationRepository.markAllRead(userId, Instant.parse("2026-06-07T00:00:00Z"))).thenReturn(2);

        assertThat(notificationService.markAllRead(userId)).isEqualTo(2L);
    }

    private Notification notification(UUID userId, String title, Instant createdAt) {
        Notification notification = new Notification();
        notification.setId(UUID.randomUUID());
        notification.setUserId(userId);
        notification.setType(NotificationType.DRIVER_VERIFICATION_EXPIRED);
        notification.setTitle(title);
        notification.setMessage("msg");
        notification.setDeliveryStatus(NotificationDeliveryStatus.SENT);
        notification.setCreatedAt(createdAt);
        return notification;
    }
}
