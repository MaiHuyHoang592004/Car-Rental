package com.rentflow.file.service;

import com.rentflow.auth.entity.Role;
import com.rentflow.booking.entity.Booking;
import com.rentflow.booking.repository.BookingRepository;
import com.rentflow.common.exception.AccessDeniedException;
import com.rentflow.file.dto.AddListingPhotoRequest;
import com.rentflow.file.dto.AddVehiclePhotoRequest;
import com.rentflow.file.dto.CreatePhotoUploadIntentRequest;
import com.rentflow.file.dto.FileUploadIntentResponse;
import com.rentflow.file.dto.ListingPhotoResponse;
import com.rentflow.file.dto.SignedFileUrlResponse;
import com.rentflow.file.dto.VehiclePhotoResponse;
import com.rentflow.file.entity.FileMetadata;
import com.rentflow.file.entity.FilePurpose;
import com.rentflow.file.entity.FileStatus;
import com.rentflow.file.entity.FileVisibility;
import com.rentflow.file.entity.ListingPhoto;
import com.rentflow.file.entity.VehiclePhoto;
import com.rentflow.file.repository.FileMetadataRepository;
import com.rentflow.file.repository.ListingPhotoRepository;
import com.rentflow.file.repository.VehiclePhotoRepository;
import com.rentflow.listing.entity.Listing;
import com.rentflow.listing.entity.ListingStatus;
import com.rentflow.listing.repository.ListingRepository;
import com.rentflow.common.security.SecurityContext;
import com.rentflow.tripcondition.repository.TripConditionPhotoRepository;
import com.rentflow.vehicle.entity.Vehicle;
import com.rentflow.vehicle.repository.VehicleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FileServiceTest {

    @Mock private FileMetadataRepository fileMetadataRepository;
    @Mock private ListingPhotoRepository listingPhotoRepository;
    @Mock private VehiclePhotoRepository vehiclePhotoRepository;
    @Mock private TripConditionPhotoRepository tripConditionPhotoRepository;
    @Mock private ListingRepository listingRepository;
    @Mock private VehicleRepository vehicleRepository;
    @Mock private BookingRepository bookingRepository;
    @Mock private SecurityContext securityContext;

    private FileService service;
    private UUID hostId;
    private UUID listingId;

    @BeforeEach
    void setUp() {
        FileSignedUrlProperties properties = new FileSignedUrlProperties();
        properties.setTtl(Duration.ofMinutes(10));
        properties.setBaseUrl("https://files.test.local");
        properties.setSecret("test-secret");
        service = new FileService(
                fileMetadataRepository,
                listingPhotoRepository,
                vehiclePhotoRepository,
                tripConditionPhotoRepository,
                listingRepository,
                vehicleRepository,
                bookingRepository,
                securityContext,
                properties);
        hostId = UUID.randomUUID();
        listingId = UUID.randomUUID();
    }

    @Test
    void createListingPhotoUploadIntentForDraftListingDefaultsToPrivate() {
        Listing listing = new Listing();
        listing.setId(listingId);
        listing.setHostId(hostId);
        listing.setStatus(ListingStatus.DRAFT);
        when(securityContext.currentUserId()).thenReturn(hostId);
        when(listingRepository.findByIdAndHostId(listingId, hostId)).thenReturn(Optional.of(listing));
        doReturn(savedPendingFile(FilePurpose.LISTING_PHOTO, FileVisibility.PRIVATE)).when(fileMetadataRepository).save(any(FileMetadata.class));

        FileUploadIntentResponse response = service.createListingPhotoUploadIntent(listingId, new CreatePhotoUploadIntentRequest(
                "image/jpeg", 1024L, null));

        ArgumentCaptor<FileMetadata> captor = ArgumentCaptor.forClass(FileMetadata.class);
        org.mockito.Mockito.verify(fileMetadataRepository).save(captor.capture());
        assertThat(captor.getValue().getPurpose()).isEqualTo(FilePurpose.LISTING_PHOTO);
        assertThat(captor.getValue().getBucket()).isEqualTo("rentflow-listing-photos");
        assertThat(captor.getValue().getVisibility()).isEqualTo(FileVisibility.PRIVATE);
        assertThat(captor.getValue().getStatus()).isEqualTo(FileStatus.PENDING_UPLOAD);
        assertThat(response.uploadUrl()).contains("https://files.test.local/files/");
    }

    @Test
    void createListingPhotoUploadIntentForActiveListingBecomesPublic() {
        Listing listing = new Listing();
        listing.setId(listingId);
        listing.setHostId(hostId);
        listing.setStatus(ListingStatus.ACTIVE);
        when(securityContext.currentUserId()).thenReturn(hostId);
        when(listingRepository.findByIdAndHostId(listingId, hostId)).thenReturn(Optional.of(listing));
        doReturn(savedPendingFile(FilePurpose.LISTING_PHOTO, FileVisibility.PUBLIC)).when(fileMetadataRepository).save(any(FileMetadata.class));

        service.createListingPhotoUploadIntent(listingId, new CreatePhotoUploadIntentRequest(
                "image/jpeg", 1024L, null));

        ArgumentCaptor<FileMetadata> captor = ArgumentCaptor.forClass(FileMetadata.class);
        org.mockito.Mockito.verify(fileMetadataRepository).save(captor.capture());
        assertThat(captor.getValue().getBucket()).isEqualTo("rentflow-listing-photos");
        assertThat(captor.getValue().getVisibility()).isEqualTo(FileVisibility.PUBLIC);
    }

    @Test
    void addListingPhotoClearsExistingPrimaryWhenNewPhotoIsPrimary() {
        Listing listing = new Listing();
        listing.setId(listingId);
        listing.setHostId(hostId);
        listing.setStatus(ListingStatus.ACTIVE);
        ListingPhoto existingPrimary = savedPhoto();
        existingPrimary.setPrimary(true);

        when(securityContext.currentUserId()).thenReturn(hostId);
        when(listingRepository.findByIdAndHostId(listingId, hostId)).thenReturn(Optional.of(listing));
        FileMetadata file = savedFile(FilePurpose.LISTING_PHOTO, FileVisibility.PUBLIC);
        file.setStatus(FileStatus.ACTIVE);
        when(fileMetadataRepository.findById(existingPrimary.getFileId())).thenReturn(Optional.of(file));
        when(fileMetadataRepository.save(any(FileMetadata.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(listingPhotoRepository.countByListingId(listingId)).thenReturn(1L);
        when(listingPhotoRepository.findByListingIdOrderByDisplayOrderAsc(listingId)).thenReturn(List.of(existingPrimary));
        doReturn(savedPhoto()).when(listingPhotoRepository).save(any(ListingPhoto.class));
        when(listingPhotoRepository.existsByFileId(existingPrimary.getFileId())).thenReturn(false);
        when(vehiclePhotoRepository.existsByFileId(existingPrimary.getFileId())).thenReturn(false);

        service.addListingPhoto(listingId, new AddListingPhotoRequest(
                existingPrimary.getFileId(), true, null));

        assertThat(existingPrimary.isPrimary()).isFalse();
    }

    @Test
    void signedUrlForPrivateFileDeniedForNonOwner() {
        UUID fileId = UUID.randomUUID();
        FileMetadata file = savedFile(FilePurpose.LISTING_PHOTO, FileVisibility.PRIVATE);
        file.setId(fileId);
        file.setOwnerUserId(UUID.randomUUID());
        when(fileMetadataRepository.findByIdAndStatus(fileId, FileStatus.ACTIVE)).thenReturn(Optional.of(file));
        when(securityContext.currentUserId()).thenReturn(UUID.randomUUID());
        when(securityContext.hasRole(Role.ADMIN)).thenReturn(false);

        assertThatThrownBy(() -> service.getSignedUrl(fileId))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void signedUrlForPublicFileAllowed() {
        UUID fileId = UUID.randomUUID();
        FileMetadata file = savedFile(FilePurpose.LISTING_PHOTO, FileVisibility.PUBLIC);
        file.setId(fileId);
        file.setOwnerUserId(UUID.randomUUID());
        when(fileMetadataRepository.findByIdAndStatus(fileId, FileStatus.ACTIVE)).thenReturn(Optional.of(file));

        SignedFileUrlResponse response = service.getSignedUrl(fileId);

        assertThat(response.fileId()).isEqualTo(fileId);
        assertThat(response.visibility()).isEqualTo("PUBLIC");
        assertThat(response.signedUrl()).contains("https://files.test.local/files/" + fileId);
    }

    @Test
    void signedUrlForPublicExternalFileReturnsExternalUrl() {
        UUID fileId = UUID.randomUUID();
        FileMetadata file = savedFile(FilePurpose.LISTING_PHOTO, FileVisibility.PUBLIC);
        file.setId(fileId);
        file.setOwnerUserId(UUID.randomUUID());
        file.setExternalUrl("https://images.example.test/car.jpg");
        when(fileMetadataRepository.findByIdAndStatus(fileId, FileStatus.ACTIVE)).thenReturn(Optional.of(file));

        SignedFileUrlResponse response = service.getSignedUrl(fileId);

        assertThat(response.fileId()).isEqualTo(fileId);
        assertThat(response.visibility()).isEqualTo("PUBLIC");
        assertThat(response.signedUrl()).isEqualTo("https://images.example.test/car.jpg");
    }

    @Test
    void addVehiclePhotoDefaultsFirstPhotoToPrimaryAndPrivate() {
        UUID vehicleId = UUID.randomUUID();
        Vehicle vehicle = new Vehicle();
        vehicle.setId(vehicleId);
        vehicle.setHostId(hostId);
        when(securityContext.currentUserId()).thenReturn(hostId);
        when(vehicleRepository.findByIdAndHostId(vehicleId, hostId)).thenReturn(Optional.of(vehicle));
        when(vehiclePhotoRepository.countByVehicleId(vehicleId)).thenReturn(0L);
        UUID fileId = UUID.randomUUID();
        FileMetadata file = savedFile(FilePurpose.VEHICLE_PHOTO, FileVisibility.PRIVATE);
        file.setId(fileId);
        file.setStatus(FileStatus.ACTIVE);
        when(fileMetadataRepository.findById(fileId)).thenReturn(Optional.of(file));
        when(fileMetadataRepository.save(any(FileMetadata.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(vehiclePhotoRepository.existsByFileId(fileId)).thenReturn(false);
        when(listingPhotoRepository.existsByFileId(fileId)).thenReturn(false);
        doReturn(savedVehiclePhoto(vehicleId, true, fileId)).when(vehiclePhotoRepository).save(any(VehiclePhoto.class));

        VehiclePhotoResponse response = service.addVehiclePhoto(vehicleId, new AddVehiclePhotoRequest(
                fileId, false, null));

        assertThat(response.primary()).isTrue();
        assertThat(response.visibility()).isEqualTo("PRIVATE");
    }

    @Test
    void finalizeUploadPromotesPendingPhotoFileToActive() {
        UUID fileId = UUID.randomUUID();
        FileMetadata file = savedPendingFile(FilePurpose.TRIP_PHOTO, FileVisibility.PRIVATE);
        file.setId(fileId);
        when(securityContext.currentUserId()).thenReturn(hostId);
        when(fileMetadataRepository.findById(fileId)).thenReturn(Optional.of(file));
        when(fileMetadataRepository.save(any(FileMetadata.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SignedFileUrlResponse response = service.finalizeUpload(fileId);

        assertThat(file.getStatus()).isEqualTo(FileStatus.ACTIVE);
        assertThat(response.fileId()).isEqualTo(fileId);
    }

    @Test
    void createTripPhotoUploadIntentRequiresBookingParticipantAndPrivateTripPhoto() {
        UUID bookingId = UUID.randomUUID();
        Booking booking = new Booking();
        booking.setId(bookingId);
        booking.setCustomerId(hostId);
        booking.setHostId(UUID.randomUUID());
        when(securityContext.currentUserId()).thenReturn(hostId);
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));
        doReturn(savedPendingFile(FilePurpose.TRIP_PHOTO, FileVisibility.PRIVATE)).when(fileMetadataRepository).save(any(FileMetadata.class));

        FileUploadIntentResponse response = service.createTripPhotoUploadIntent(
                bookingId,
                new CreatePhotoUploadIntentRequest("image/png", 2048L, null));

        ArgumentCaptor<FileMetadata> captor = ArgumentCaptor.forClass(FileMetadata.class);
        org.mockito.Mockito.verify(fileMetadataRepository).save(captor.capture());
        assertThat(captor.getValue().getPurpose()).isEqualTo(FilePurpose.TRIP_PHOTO);
        assertThat(captor.getValue().getBucket()).isEqualTo("rentflow-trip-photos");
        assertThat(captor.getValue().getObjectKey()).startsWith("trips/" + bookingId + "/");
        assertThat(captor.getValue().getVisibility()).isEqualTo(FileVisibility.PRIVATE);
        assertThat(response.uploadUrl()).contains("action=dXBsb2Fk");
    }

    @Test
    void requireAttachableTripPhotoRejectsAlreadyAttachedFile() {
        UUID fileId = UUID.randomUUID();
        FileMetadata file = savedFile(FilePurpose.TRIP_PHOTO, FileVisibility.PRIVATE);
        file.setId(fileId);
        when(fileMetadataRepository.findById(fileId)).thenReturn(Optional.of(file));
        when(tripConditionPhotoRepository.existsByFileId(fileId)).thenReturn(true);

        assertThatThrownBy(() -> service.requireAttachableTripPhotoFile(fileId, hostId))
                .isInstanceOf(com.rentflow.common.exception.ValidationException.class)
                .hasMessageContaining("already attached");
    }

    @Test
    void addVehiclePhotoRejectsNonFinalizedFile() {
        UUID vehicleId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        Vehicle vehicle = new Vehicle();
        vehicle.setId(vehicleId);
        vehicle.setHostId(hostId);
        FileMetadata file = savedPendingFile(FilePurpose.VEHICLE_PHOTO, FileVisibility.PRIVATE);
        file.setId(fileId);
        when(securityContext.currentUserId()).thenReturn(hostId);
        when(vehicleRepository.findByIdAndHostId(vehicleId, hostId)).thenReturn(Optional.of(vehicle));
        when(vehiclePhotoRepository.countByVehicleId(vehicleId)).thenReturn(0L);
        when(fileMetadataRepository.findById(fileId)).thenReturn(Optional.of(file));

        assertThatThrownBy(() -> service.addVehiclePhoto(vehicleId, new AddVehiclePhotoRequest(fileId, true, null)))
                .isInstanceOf(com.rentflow.common.exception.ValidationException.class)
                .hasMessageContaining("finalized");
    }

    private FileMetadata savedFile(FilePurpose purpose, FileVisibility visibility) {
        FileMetadata file = new FileMetadata();
        file.setId(UUID.randomUUID());
        file.setOwnerUserId(hostId);
        file.setBucket("bucket-a");
        file.setObjectKey("path/key.jpg");
        file.setContentType("image/jpeg");
        file.setSizeBytes(1000L);
        file.setPurpose(purpose);
        file.setVisibility(visibility);
        file.setStatus(FileStatus.ACTIVE);
        return file;
    }

    private FileMetadata savedPendingFile(FilePurpose purpose, FileVisibility visibility) {
        FileMetadata file = savedFile(purpose, visibility);
        file.setStatus(FileStatus.PENDING_UPLOAD);
        return file;
    }

    private ListingPhoto savedPhoto() {
        ListingPhoto photo = new ListingPhoto();
        photo.setId(UUID.randomUUID());
        photo.setListingId(listingId);
        photo.setFileId(UUID.randomUUID());
        photo.setDisplayOrder(0);
        photo.setPrimary(true);
        return photo;
    }

    private VehiclePhoto savedVehiclePhoto(UUID vehicleId, boolean primary, UUID fileId) {
        VehiclePhoto photo = new VehiclePhoto();
        photo.setId(UUID.randomUUID());
        photo.setVehicleId(vehicleId);
        photo.setFileId(fileId);
        photo.setDisplayOrder(0);
        photo.setPrimary(primary);
        return photo;
    }
}
