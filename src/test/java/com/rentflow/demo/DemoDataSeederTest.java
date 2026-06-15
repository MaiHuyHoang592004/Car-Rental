package com.rentflow.demo;

import com.rentflow.auth.entity.AuthUser;
import com.rentflow.auth.entity.Role;
import com.rentflow.auth.entity.UserStatus;
import com.rentflow.auth.repository.AuthUserRepository;
import com.rentflow.auth.repository.UserRoleRepository;
import com.rentflow.availability.repository.AvailabilityCalendarRepository;
import com.rentflow.common.BaseEntity;
import com.rentflow.file.entity.FileMetadata;
import com.rentflow.file.entity.ListingPhoto;
import com.rentflow.file.repository.FileMetadataRepository;
import com.rentflow.file.repository.ListingPhotoRepository;
import com.rentflow.listing.entity.Listing;
import com.rentflow.listing.repository.ListingRepository;
import com.rentflow.notification.entity.Notification;
import com.rentflow.notification.entity.NotificationType;
import com.rentflow.notification.repository.NotificationRepository;
import com.rentflow.user.entity.UserProfile;
import com.rentflow.user.repository.UserProfileRepository;
import com.rentflow.vehicle.entity.Vehicle;
import com.rentflow.vehicle.repository.VehicleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DemoDataSeederTest {

    @Mock private AuthUserRepository authUserRepository;
    @Mock private UserRoleRepository userRoleRepository;
    @Mock private UserProfileRepository userProfileRepository;
    @Mock private VehicleRepository vehicleRepository;
    @Mock private ListingRepository listingRepository;
    @Mock private AvailabilityCalendarRepository availabilityRepository;
    @Mock private FileMetadataRepository fileMetadataRepository;
    @Mock private ListingPhotoRepository listingPhotoRepository;
    @Mock private NotificationRepository notificationRepository;
    @Mock private PasswordEncoder passwordEncoder;

    private DemoDataProperties properties;
    private DemoDataSeeder seeder;

    @BeforeEach
    void setUp() {
        properties = new DemoDataProperties();
        properties.setEnabled(true);
        seeder = new DemoDataSeeder(
                properties,
                authUserRepository,
                userRoleRepository,
                userProfileRepository,
                vehicleRepository,
                listingRepository,
                availabilityRepository,
                fileMetadataRepository,
                listingPhotoRepository,
                notificationRepository,
                passwordEncoder,
                Clock.fixed(Instant.parse("2026-06-15T00:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void runCreatesSixPublicListingsWhenMissing() {
        when(authUserRepository.findByEmail("demo-host@rentflow.local")).thenReturn(Optional.empty());
        when(authUserRepository.findByEmail("demo-customer@rentflow.local")).thenReturn(Optional.empty());
        when(passwordEncoder.encode(anyString())).thenReturn("hash");
        when(authUserRepository.save(any(AuthUser.class))).thenAnswer(invocation -> assignId(invocation.getArgument(0)));
        when(userRoleRepository.existsByUserIdAndRole(any(UUID.class), any(Role.class))).thenReturn(false);
        when(userProfileRepository.findByUserId(any(UUID.class))).thenReturn(Optional.empty());
        when(listingRepository.findByTitle(anyString())).thenReturn(Optional.empty());
        when(vehicleRepository.save(any(Vehicle.class))).thenAnswer(invocation -> assignId(invocation.getArgument(0)));
        when(listingRepository.save(any(Listing.class))).thenAnswer(invocation -> assignId(invocation.getArgument(0)));
        when(availabilityRepository.countByListingId(any(UUID.class))).thenReturn(0L);
        when(fileMetadataRepository.findByBucketAndObjectKey(anyString(), anyString())).thenReturn(Optional.empty());
        when(fileMetadataRepository.save(any(FileMetadata.class))).thenAnswer(invocation -> assignId(invocation.getArgument(0)));
        when(listingPhotoRepository.countByListingId(any(UUID.class))).thenReturn(0L);
        when(listingPhotoRepository.save(any(ListingPhoto.class))).thenAnswer(invocation -> assignId(invocation.getArgument(0)));
        when(notificationRepository.findByUserIdAndTypeAndTitle(any(UUID.class), any(NotificationType.class), anyString()))
                .thenReturn(Optional.empty());
        when(notificationRepository.save(any(Notification.class))).thenAnswer(invocation -> assignId(invocation.getArgument(0)));

        seeder.run(null);

        verify(vehicleRepository, times(6)).save(any(Vehicle.class));
        verify(listingRepository, times(6)).save(any(Listing.class));
        verify(availabilityRepository, times(6)).insertAvailabilityRange(any(UUID.class), any(), any());
        verify(listingPhotoRepository, times(6)).save(any(ListingPhoto.class));
        verify(notificationRepository, times(4)).save(any(Notification.class));
        ArgumentCaptor<UserProfile> profileCaptor = ArgumentCaptor.forClass(UserProfile.class);
        verify(userProfileRepository, times(2)).save(profileCaptor.capture());
        assertThat(profileCaptor.getAllValues())
                .allSatisfy(profile -> {
                    assertThat(profile.getUser()).isNotNull();
                    assertThat(profile.getUser().getId()).isNotNull();
                    assertThat(profile.getUserId()).isNull();
                });
    }

    @Test
    void runSkipsExistingListingsWithoutCreatingDuplicates() {
        AuthUser host = new AuthUser("demo-host@rentflow.local", "hash", UserStatus.ACTIVE, true);
        host.setId(UUID.randomUUID());
        AuthUser customer = new AuthUser("demo-customer@rentflow.local", "hash", UserStatus.ACTIVE, true);
        customer.setId(UUID.randomUUID());
        UserProfile profile = new UserProfile("RentFlow Demo Host");
        Listing existing = new Listing();
        existing.setId(UUID.randomUUID());
        Notification existingNotification = new Notification();
        existingNotification.setId(UUID.randomUUID());

        when(authUserRepository.findByEmail("demo-host@rentflow.local")).thenReturn(Optional.of(host));
        when(authUserRepository.findByEmail("demo-customer@rentflow.local")).thenReturn(Optional.of(customer));
        when(userRoleRepository.existsByUserIdAndRole(host.getId(), Role.HOST)).thenReturn(true);
        when(userRoleRepository.existsByUserIdAndRole(customer.getId(), Role.CUSTOMER)).thenReturn(true);
        when(userProfileRepository.findByUserId(host.getId())).thenReturn(Optional.of(profile));
        when(userProfileRepository.findByUserId(customer.getId())).thenReturn(Optional.of(profile));
        when(listingRepository.findByTitle(anyString())).thenReturn(Optional.of(existing));
        when(availabilityRepository.countByListingId(existing.getId())).thenReturn(1L);
        when(listingPhotoRepository.countByListingId(existing.getId())).thenReturn(1L);
        when(notificationRepository.findByUserIdAndTypeAndTitle(any(UUID.class), any(NotificationType.class), anyString()))
                .thenReturn(Optional.of(existingNotification));

        seeder.run(null);

        verify(vehicleRepository, never()).save(any(Vehicle.class));
        verify(listingRepository, never()).save(any(Listing.class));
        verify(availabilityRepository, never()).insertAvailabilityRange(any(UUID.class), any(), any());
        verify(listingPhotoRepository, never()).save(any(ListingPhoto.class));
        verify(notificationRepository, never()).save(any(Notification.class));
    }

    @Test
    void runDoesNothingWhenDisabled() {
        properties.setEnabled(false);

        seeder.run(null);

        verifyNoInteractions(authUserRepository, vehicleRepository, listingRepository, notificationRepository);
    }

    private <T> T assignId(T entity) {
        if (entity instanceof BaseEntity baseEntity && baseEntity.getId() == null) {
            baseEntity.setId(UUID.randomUUID());
        }
        return entity;
    }
}
