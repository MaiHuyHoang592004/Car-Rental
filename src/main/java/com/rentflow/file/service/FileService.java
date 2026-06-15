package com.rentflow.file.service;

import com.rentflow.auth.entity.Role;
import com.rentflow.booking.entity.Booking;
import com.rentflow.booking.repository.BookingRepository;
import com.rentflow.common.exception.AccessDeniedException;
import com.rentflow.common.exception.BookingNotFoundException;
import com.rentflow.common.exception.ListingNotFoundException;
import com.rentflow.common.exception.ResourceNotFoundException;
import com.rentflow.common.exception.ValidationException;
import com.rentflow.common.exception.VehicleNotFoundException;
import com.rentflow.common.security.SecurityContext;
import com.rentflow.file.dto.AddListingPhotoRequest;
import com.rentflow.file.dto.AddVehiclePhotoRequest;
import com.rentflow.file.dto.CreateDisputeAttachmentUploadRequest;
import com.rentflow.file.dto.CreatePhotoUploadIntentRequest;
import com.rentflow.file.dto.FileUploadIntentResponse;
import com.rentflow.file.dto.ListingPhotoResponse;
import com.rentflow.file.dto.SignedFileUrlResponse;
import com.rentflow.file.dto.UpdateListingPhotoRequest;
import com.rentflow.file.dto.UpdateVehiclePhotoRequest;
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
import com.rentflow.tripcondition.repository.TripConditionPhotoRepository;
import com.rentflow.vehicle.entity.Vehicle;
import com.rentflow.vehicle.repository.VehicleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.time.Instant;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class FileService {

    private static final long PHOTO_MAX_BYTES = 10L * 1024 * 1024;
    private static final long DISPUTE_ATTACHMENT_MAX_BYTES = 10L * 1024 * 1024;
    private static final long VEHICLE_PHOTO_MAX_COUNT = 8L;
    private static final String VEHICLE_PHOTO_BUCKET = "rentflow-vehicle-photos";
    private static final String LISTING_PHOTO_BUCKET = "rentflow-listing-photos";
    private static final String DISPUTE_ATTACHMENT_BUCKET = "rentflow-dispute-attachments";
    private static final String TRIP_PHOTO_BUCKET = "rentflow-trip-photos";

    private final FileMetadataRepository fileMetadataRepository;
    private final ListingPhotoRepository listingPhotoRepository;
    private final VehiclePhotoRepository vehiclePhotoRepository;
    private final TripConditionPhotoRepository tripConditionPhotoRepository;
    private final ListingRepository listingRepository;
    private final VehicleRepository vehicleRepository;
    private final BookingRepository bookingRepository;
    private final SecurityContext securityContext;
    private final FileSignedUrlProperties signedUrlProperties;

    public FileService(
            FileMetadataRepository fileMetadataRepository,
            ListingPhotoRepository listingPhotoRepository,
            VehiclePhotoRepository vehiclePhotoRepository,
            TripConditionPhotoRepository tripConditionPhotoRepository,
            ListingRepository listingRepository,
            VehicleRepository vehicleRepository,
            BookingRepository bookingRepository,
            SecurityContext securityContext,
            FileSignedUrlProperties signedUrlProperties) {
        this.fileMetadataRepository = fileMetadataRepository;
        this.listingPhotoRepository = listingPhotoRepository;
        this.vehiclePhotoRepository = vehiclePhotoRepository;
        this.tripConditionPhotoRepository = tripConditionPhotoRepository;
        this.listingRepository = listingRepository;
        this.vehicleRepository = vehicleRepository;
        this.bookingRepository = bookingRepository;
        this.securityContext = securityContext;
        this.signedUrlProperties = signedUrlProperties;
    }

    @Transactional
    public FileUploadIntentResponse createListingPhotoUploadIntent(UUID listingId, CreatePhotoUploadIntentRequest request) {
        validatePhotoInput(request.contentType(), request.sizeBytes(), "Listing photo");
        UUID hostId = securityContext.currentUserId();
        Listing listing = listingRepository.findByIdAndHostId(listingId, hostId)
                .orElseThrow(() -> new ListingNotFoundException(listingId.toString()));

        FileMetadata metadata = createPendingPhotoMetadata(
                hostId,
                FilePurpose.LISTING_PHOTO,
                LISTING_PHOTO_BUCKET,
                "listings/" + listingId + "/" + UUID.randomUUID(),
                request,
                listing.getStatus() == ListingStatus.ACTIVE ? FileVisibility.PUBLIC : FileVisibility.PRIVATE);
        Signed signed = buildSignedUrl(metadata, "upload");
        return new FileUploadIntentResponse(
                metadata.getId(),
                metadata.getBucket(),
                metadata.getObjectKey(),
                signed.url(),
                signed.expiresAt());
    }

    @Transactional
    public ListingPhotoResponse addListingPhoto(UUID listingId, AddListingPhotoRequest request) {
        UUID hostId = securityContext.currentUserId();
        Listing listing = listingRepository.findByIdAndHostId(listingId, hostId)
                .orElseThrow(() -> new ListingNotFoundException(listingId.toString()));
        FileMetadata metadata = requireAttachablePhotoFile(request.fileId(), hostId, FilePurpose.LISTING_PHOTO);
        metadata.setVisibility(listing.getStatus() == ListingStatus.ACTIVE ? FileVisibility.PUBLIC : FileVisibility.PRIVATE);
        metadata = fileMetadataRepository.save(metadata);

        int displayOrder = request.displayOrder() != null
                ? request.displayOrder()
                : (int) listingPhotoRepository.countByListingId(listingId);
        boolean primary = Boolean.TRUE.equals(request.primary()) || listingPhotoRepository.countByListingId(listingId) == 0;
        if (primary) {
            clearListingPrimary(listingId);
        }

        ListingPhoto photo = new ListingPhoto();
        photo.setListingId(listingId);
        photo.setFileId(metadata.getId());
        photo.setDisplayOrder(displayOrder);
        photo.setPrimary(primary);
        photo = listingPhotoRepository.save(photo);
        return toListingPhotoResponse(photo, metadata);
    }

    @Transactional(readOnly = true)
    public List<ListingPhotoResponse> listListingPhotos(UUID listingId) {
        UUID hostId = securityContext.currentUserId();
        assertListingOwner(listingId, hostId);
        return listingPhotoRepository.findByListingIdOrderByDisplayOrderAsc(listingId).stream()
                .map(this::toListingPhotoResponse)
                .toList();
    }

    @Transactional
    public ListingPhotoResponse updateListingPhoto(UUID listingId, UUID photoId, UpdateListingPhotoRequest request) {
        UUID hostId = securityContext.currentUserId();
        assertListingOwner(listingId, hostId);
        ListingPhoto photo = listingPhotoRepository.findByIdAndListingId(photoId, listingId)
                .orElseThrow(() -> new ResourceNotFoundException("LISTING_PHOTO_NOT_FOUND", "ListingPhoto", photoId.toString()));

        if (request.displayOrder() != null) {
            photo.setDisplayOrder(request.displayOrder());
        }
        if (Boolean.TRUE.equals(request.primary())) {
            clearListingPrimary(listingId);
            photo.setPrimary(true);
        } else if (Boolean.FALSE.equals(request.primary())) {
            photo.setPrimary(false);
        }

        photo = listingPhotoRepository.save(photo);
        return toListingPhotoResponse(photo);
    }

    @Transactional
    public void deleteListingPhoto(UUID listingId, UUID photoId) {
        UUID hostId = securityContext.currentUserId();
        assertListingOwner(listingId, hostId);
        ListingPhoto photo = listingPhotoRepository.findByIdAndListingId(photoId, listingId)
                .orElseThrow(() -> new ResourceNotFoundException("LISTING_PHOTO_NOT_FOUND", "ListingPhoto", photoId.toString()));
        boolean wasPrimary = photo.isPrimary();
        listingPhotoRepository.delete(photo);

        if (wasPrimary) {
            listingPhotoRepository.findByListingIdOrderByDisplayOrderAsc(listingId).stream()
                    .findFirst()
                    .ifPresent(next -> {
                        next.setPrimary(true);
                        listingPhotoRepository.save(next);
                    });
        }
    }

    @Transactional
    public FileUploadIntentResponse createVehiclePhotoUploadIntent(UUID vehicleId, CreatePhotoUploadIntentRequest request) {
        validatePhotoInput(request.contentType(), request.sizeBytes(), "Vehicle photo");
        UUID hostId = securityContext.currentUserId();
        assertVehicleOwner(vehicleId, hostId);

        long currentCount = vehiclePhotoRepository.countByVehicleId(vehicleId);
        if (currentCount >= VEHICLE_PHOTO_MAX_COUNT) {
            throw new ValidationException("Vehicle can have at most 8 photos");
        }

        FileMetadata metadata = createPendingPhotoMetadata(
                hostId,
                FilePurpose.VEHICLE_PHOTO,
                VEHICLE_PHOTO_BUCKET,
                "vehicles/" + vehicleId + "/" + UUID.randomUUID(),
                request,
                FileVisibility.PRIVATE);
        Signed signed = buildSignedUrl(metadata, "upload");
        return new FileUploadIntentResponse(
                metadata.getId(),
                metadata.getBucket(),
                metadata.getObjectKey(),
                signed.url(),
                signed.expiresAt());
    }

    @Transactional
    public VehiclePhotoResponse addVehiclePhoto(UUID vehicleId, AddVehiclePhotoRequest request) {
        UUID hostId = securityContext.currentUserId();
        assertVehicleOwner(vehicleId, hostId);
        long currentCount = vehiclePhotoRepository.countByVehicleId(vehicleId);
        if (currentCount >= VEHICLE_PHOTO_MAX_COUNT) {
            throw new ValidationException("Vehicle can have at most 8 photos");
        }
        FileMetadata metadata = requireAttachablePhotoFile(request.fileId(), hostId, FilePurpose.VEHICLE_PHOTO);
        metadata.setVisibility(FileVisibility.PRIVATE);
        metadata = fileMetadataRepository.save(metadata);

        boolean primary = Boolean.TRUE.equals(request.primary()) || currentCount == 0;
        if (primary) {
            clearVehiclePrimary(vehicleId);
        }

        VehiclePhoto photo = new VehiclePhoto();
        photo.setVehicleId(vehicleId);
        photo.setFileId(metadata.getId());
        photo.setDisplayOrder(request.displayOrder() != null ? request.displayOrder() : (int) currentCount);
        photo.setPrimary(primary);
        photo = vehiclePhotoRepository.save(photo);

        return toVehiclePhotoResponse(photo, metadata);
    }

    @Transactional(readOnly = true)
    public List<VehiclePhotoResponse> listVehiclePhotos(UUID vehicleId) {
        UUID hostId = securityContext.currentUserId();
        assertVehicleOwner(vehicleId, hostId);
        return vehiclePhotoRepository.findByVehicleIdOrderByDisplayOrderAsc(vehicleId).stream()
                .map(this::toVehiclePhotoResponse)
                .toList();
    }

    @Transactional
    public VehiclePhotoResponse updateVehiclePhoto(UUID vehicleId, UUID photoId, UpdateVehiclePhotoRequest request) {
        UUID hostId = securityContext.currentUserId();
        assertVehicleOwner(vehicleId, hostId);
        VehiclePhoto photo = vehiclePhotoRepository.findByIdAndVehicleId(photoId, vehicleId)
                .orElseThrow(() -> new ResourceNotFoundException("VEHICLE_PHOTO_NOT_FOUND", "VehiclePhoto", photoId.toString()));

        if (request.displayOrder() != null) {
            photo.setDisplayOrder(request.displayOrder());
        }
        if (Boolean.TRUE.equals(request.primary())) {
            clearVehiclePrimary(vehicleId);
            photo.setPrimary(true);
        } else if (Boolean.FALSE.equals(request.primary())) {
            photo.setPrimary(false);
        }

        photo = vehiclePhotoRepository.save(photo);
        return toVehiclePhotoResponse(photo);
    }

    @Transactional
    public void deleteVehiclePhoto(UUID vehicleId, UUID photoId) {
        UUID hostId = securityContext.currentUserId();
        assertVehicleOwner(vehicleId, hostId);
        VehiclePhoto photo = vehiclePhotoRepository.findByIdAndVehicleId(photoId, vehicleId)
                .orElseThrow(() -> new ResourceNotFoundException("VEHICLE_PHOTO_NOT_FOUND", "VehiclePhoto", photoId.toString()));
        boolean wasPrimary = photo.isPrimary();
        vehiclePhotoRepository.delete(photo);

        if (wasPrimary) {
            vehiclePhotoRepository.findByVehicleIdOrderByDisplayOrderAsc(vehicleId).stream()
                    .findFirst()
                    .ifPresent(next -> {
                        next.setPrimary(true);
                        vehiclePhotoRepository.save(next);
                    });
        }
    }

    @Transactional
    public void seedListingPhotosFromVehicle(UUID listingId, UUID vehicleId) {
        if (listingPhotoRepository.countByListingId(listingId) > 0) {
            return;
        }
        List<VehiclePhoto> vehiclePhotos = vehiclePhotoRepository.findByVehicleIdOrderByDisplayOrderAsc(vehicleId);
        for (VehiclePhoto vehiclePhoto : vehiclePhotos) {
            ListingPhoto listingPhoto = new ListingPhoto();
            listingPhoto.setListingId(listingId);
            listingPhoto.setFileId(vehiclePhoto.getFileId());
            listingPhoto.setDisplayOrder(vehiclePhoto.getDisplayOrder());
            listingPhoto.setPrimary(vehiclePhoto.isPrimary());
            listingPhotoRepository.save(listingPhoto);
        }
    }

    @Transactional(readOnly = true)
    public List<String> getListingPhotoUrls(UUID listingId, UUID vehicleId) {
        List<ListingPhoto> listingPhotos = listingPhotoRepository.findByListingIdOrderByDisplayOrderAsc(listingId);
        if (!listingPhotos.isEmpty()) {
            return listingPhotos.stream()
                    .sorted(photoComparator())
                    .map(this::signedUrlForPhoto)
                    .toList();
        }
        return vehiclePhotoRepository.findByVehicleIdOrderByDisplayOrderAsc(vehicleId).stream()
                .sorted(vehiclePhotoComparator())
                .map(this::signedUrlForVehiclePhoto)
                .toList();
    }

    @Transactional(readOnly = true)
    public Map<UUID, String> getCoverPhotoUrls(Collection<UUID> listingIds) {
        if (listingIds == null || listingIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, String> urls = new LinkedHashMap<>();
        listingPhotoRepository.findByListingIdInOrderByDisplayOrderAsc(listingIds).stream()
                .sorted(photoComparator())
                .forEach(photo -> urls.putIfAbsent(photo.getListingId(), signedUrlForPhoto(photo)));
        return urls;
    }

    @Transactional(readOnly = true)
    public Map<UUID, String> getCoverPhotoUrls(Map<UUID, UUID> listingVehicleIds) {
        if (listingVehicleIds == null || listingVehicleIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, String> urls = new LinkedHashMap<>(getCoverPhotoUrls(listingVehicleIds.keySet()));
        listingVehicleIds.forEach((listingId, vehicleId) -> {
            if (urls.containsKey(listingId)) {
                return;
            }
            vehiclePhotoRepository.findByVehicleIdOrderByDisplayOrderAsc(vehicleId).stream()
                    .sorted(vehiclePhotoComparator())
                    .findFirst()
                    .ifPresent(photo -> urls.put(listingId, signedUrlForVehiclePhoto(photo)));
        });
        return urls;
    }

    @Transactional(readOnly = true)
    public SignedFileUrlResponse getSignedUrl(UUID fileId) {
        FileMetadata file = fileMetadataRepository.findByIdAndStatus(fileId, FileStatus.ACTIVE)
                .orElseThrow(() -> new ResourceNotFoundException("FILE_NOT_FOUND", "File", fileId.toString()));
        assertReadable(file);
        Signed signed = buildSignedUrl(file);
        return new SignedFileUrlResponse(file.getId(), file.getVisibility().name(), signed.url(), signed.expiresAt());
    }

    @Transactional
    public FileUploadIntentResponse createTripPhotoUploadIntent(UUID bookingId, CreatePhotoUploadIntentRequest request) {
        validatePhotoInput(request.contentType(), request.sizeBytes(), "Trip photo");
        UUID actorId = securityContext.currentUserId();
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new BookingNotFoundException(bookingId.toString()));
        if (!booking.getCustomerId().equals(actorId) && !booking.getHostId().equals(actorId)) {
            throw new BookingNotFoundException(bookingId.toString());
        }

        FileMetadata metadata = createPendingPhotoMetadata(
                actorId,
                FilePurpose.TRIP_PHOTO,
                TRIP_PHOTO_BUCKET,
                "trips/" + bookingId + "/" + UUID.randomUUID(),
                request,
                FileVisibility.PRIVATE);
        Signed signed = buildSignedUrl(metadata, "upload");
        return new FileUploadIntentResponse(
                metadata.getId(),
                metadata.getBucket(),
                metadata.getObjectKey(),
                signed.url(),
                signed.expiresAt());
    }

    @Transactional(readOnly = true)
    public SignedFileUrlResponse getSignedUrlIfExists(UUID fileId) {
        FileMetadata file = fileMetadataRepository.findByIdAndStatus(fileId, FileStatus.ACTIVE)
                .orElse(null);
        if (file == null) {
            return null;
        }
        assertReadable(file);
        Signed signed = buildSignedUrl(file);
        return new SignedFileUrlResponse(file.getId(), file.getVisibility().name(), signed.url(), signed.expiresAt());
    }

    @Transactional
    public FileUploadIntentResponse createDisputeAttachmentUploadIntent(CreateDisputeAttachmentUploadRequest request) {
        validateDisputeAttachmentInput(request.contentType(), request.sizeBytes());
        UUID ownerId = securityContext.currentUserId();

        FileMetadata metadata = new FileMetadata();
        metadata.setOwnerUserId(ownerId);
        metadata.setPurpose(FilePurpose.DISPUTE_ATTACHMENT);
        metadata.setBucket(DISPUTE_ATTACHMENT_BUCKET);
        metadata.setObjectKey("disputes/" + ownerId + "/" + UUID.randomUUID());
        metadata.setContentType(request.contentType().trim().toLowerCase());
        metadata.setSizeBytes(request.sizeBytes());
        metadata.setChecksum(request.checksum());
        metadata.setVisibility(FileVisibility.PRIVATE);
        metadata.setStatus(FileStatus.PENDING_UPLOAD);
        metadata = fileMetadataRepository.save(metadata);

        Signed signed = buildSignedUrl(metadata, "upload");
        return new FileUploadIntentResponse(
                metadata.getId(),
                metadata.getBucket(),
                metadata.getObjectKey(),
                signed.url(),
                signed.expiresAt());
    }

    @Transactional
    public SignedFileUrlResponse finalizeUpload(UUID fileId) {
        FileMetadata metadata = fileMetadataRepository.findById(fileId)
                .orElseThrow(() -> new ResourceNotFoundException("FILE_NOT_FOUND", "File", fileId.toString()));
        if (!metadata.getOwnerUserId().equals(securityContext.currentUserId())) {
            throw new AccessDeniedException();
        }
        if (metadata.getStatus() == FileStatus.DELETED) {
            throw new ResourceNotFoundException("FILE_NOT_FOUND", "File", fileId.toString());
        }
        if (metadata.getPurpose() != FilePurpose.DISPUTE_ATTACHMENT
                && metadata.getPurpose() != FilePurpose.VEHICLE_PHOTO
                && metadata.getPurpose() != FilePurpose.LISTING_PHOTO
                && metadata.getPurpose() != FilePurpose.TRIP_PHOTO) {
            throw new ValidationException("File purpose does not support finalize upload");
        }
        if (metadata.getStatus() == FileStatus.PENDING_UPLOAD) {
            metadata.setStatus(FileStatus.ACTIVE);
            metadata = fileMetadataRepository.save(metadata);
        }
        Signed signed = buildSignedUrl(metadata);
        return new SignedFileUrlResponse(metadata.getId(), metadata.getVisibility().name(), signed.url(), signed.expiresAt());
    }

    @Transactional(readOnly = true)
    public void requireAttachableDisputeFile(UUID fileId, UUID ownerId) {
        FileMetadata file = fileMetadataRepository.findByIdAndStatus(fileId, FileStatus.ACTIVE)
                .orElseThrow(() -> new ResourceNotFoundException("FILE_NOT_FOUND", "File", fileId.toString()));
        if (file.getPurpose() != FilePurpose.DISPUTE_ATTACHMENT) {
            throw new ValidationException("File is not a dispute attachment");
        }
        if (!file.getOwnerUserId().equals(ownerId)) {
            throw new AccessDeniedException();
        }
    }

    @Transactional(readOnly = true)
    public void requireAttachableTripPhotoFile(UUID fileId, UUID ownerId) {
        FileMetadata file = fileMetadataRepository.findById(fileId)
                .orElseThrow(() -> new ResourceNotFoundException("FILE_NOT_FOUND", "File", fileId.toString()));
        if (file.getPurpose() != FilePurpose.TRIP_PHOTO) {
            throw new ValidationException("File is not a trip photo");
        }
        if (!file.getOwnerUserId().equals(ownerId)) {
            throw new AccessDeniedException();
        }
        if (file.getStatus() != FileStatus.ACTIVE) {
            throw new ValidationException("File upload must be finalized before attaching trip photo");
        }
        if (tripConditionPhotoRepository.existsByFileId(fileId)) {
            throw new ValidationException("File is already attached to a trip condition report");
        }
    }

    @Transactional(readOnly = true)
    public SignedFileUrlResponse getTripPhotoSignedUrlForAuthorizedViewer(UUID fileId) {
        FileMetadata file = fileMetadataRepository.findByIdAndStatus(fileId, FileStatus.ACTIVE)
                .orElseThrow(() -> new ResourceNotFoundException("FILE_NOT_FOUND", "File", fileId.toString()));
        if (file.getPurpose() != FilePurpose.TRIP_PHOTO) {
            throw new ValidationException("File is not a trip photo");
        }
        Signed signed = buildSignedUrl(file);
        return new SignedFileUrlResponse(file.getId(), file.getVisibility().name(), signed.url(), signed.expiresAt());
    }

    private void validatePhotoInput(String contentType, Long sizeBytes, String label) {
        if (contentType == null || contentType.isBlank()) {
            throw new ValidationException(label + " contentType must not be blank");
        }
        if (!contentType.toLowerCase().startsWith("image/")) {
            throw new ValidationException(label + " contentType must be an image/* MIME type");
        }
        if (sizeBytes == null || sizeBytes <= 0) {
            throw new ValidationException(label + " size must be > 0");
        }
        if (sizeBytes > PHOTO_MAX_BYTES) {
            throw new ValidationException(label + " must be <= 10MB");
        }
    }

    private FileMetadata createPendingPhotoMetadata(
            UUID ownerId,
            FilePurpose purpose,
            String bucket,
            String objectKey,
            CreatePhotoUploadIntentRequest request,
            FileVisibility visibility) {
        FileMetadata metadata = new FileMetadata();
        metadata.setOwnerUserId(ownerId);
        metadata.setPurpose(purpose);
        metadata.setBucket(bucket);
        metadata.setObjectKey(objectKey);
        metadata.setContentType(request.contentType().trim().toLowerCase());
        metadata.setSizeBytes(request.sizeBytes());
        metadata.setChecksum(request.checksum());
        metadata.setVisibility(visibility);
        metadata.setStatus(FileStatus.PENDING_UPLOAD);
        return fileMetadataRepository.save(metadata);
    }

    private FileMetadata requireAttachablePhotoFile(UUID fileId, UUID ownerId, FilePurpose expectedPurpose) {
        FileMetadata metadata = fileMetadataRepository.findById(fileId)
                .orElseThrow(() -> new ResourceNotFoundException("FILE_NOT_FOUND", "File", fileId.toString()));
        if (!metadata.getOwnerUserId().equals(ownerId)) {
            throw new AccessDeniedException();
        }
        if (metadata.getPurpose() != expectedPurpose) {
            throw new ValidationException("File purpose does not match photo type");
        }
        if (metadata.getStatus() != FileStatus.ACTIVE) {
            throw new ValidationException("File upload must be finalized before attaching photo");
        }
        if (listingPhotoRepository.existsByFileId(fileId) || vehiclePhotoRepository.existsByFileId(fileId)) {
            throw new ValidationException("File is already attached to a photo");
        }
        return metadata;
    }

    private void validateDisputeAttachmentInput(String contentType, Long sizeBytes) {
        if (contentType == null || contentType.isBlank()) {
            throw new ValidationException("Dispute attachment contentType must not be blank");
        }
        String normalized = contentType.toLowerCase();
        if (!normalized.startsWith("image/") && !"application/pdf".equals(normalized)) {
            throw new ValidationException("Dispute attachment must be image/* or application/pdf");
        }
        if (sizeBytes == null || sizeBytes <= 0 || sizeBytes > DISPUTE_ATTACHMENT_MAX_BYTES) {
            throw new ValidationException("Dispute attachment must be <= 10MB");
        }
    }

    private void assertVehicleOwner(UUID vehicleId, UUID hostId) {
        Vehicle vehicle = vehicleRepository.findByIdAndHostId(vehicleId, hostId)
                .orElseThrow(() -> new VehicleNotFoundException(vehicleId.toString()));
        if (!vehicle.getHostId().equals(hostId)) {
            throw new AccessDeniedException();
        }
    }

    private void assertListingOwner(UUID listingId, UUID hostId) {
        listingRepository.findByIdAndHostId(listingId, hostId)
                .orElseThrow(() -> new ListingNotFoundException(listingId.toString()));
    }

    private void clearListingPrimary(UUID listingId) {
        listingPhotoRepository.findByListingIdOrderByDisplayOrderAsc(listingId).forEach(photo -> {
            if (photo.isPrimary()) {
                photo.setPrimary(false);
                listingPhotoRepository.save(photo);
            }
        });
    }

    private void clearVehiclePrimary(UUID vehicleId) {
        vehiclePhotoRepository.findByVehicleIdOrderByDisplayOrderAsc(vehicleId).forEach(photo -> {
            if (photo.isPrimary()) {
                photo.setPrimary(false);
                vehiclePhotoRepository.save(photo);
            }
        });
    }

    private VehiclePhotoResponse toVehiclePhotoResponse(VehiclePhoto photo) {
        FileMetadata metadata = fileMetadataRepository.findByIdAndStatus(photo.getFileId(), FileStatus.ACTIVE)
                .orElseThrow(() -> new ResourceNotFoundException("FILE_NOT_FOUND", "File", photo.getFileId().toString()));
        return toVehiclePhotoResponse(photo, metadata);
    }

    private VehiclePhotoResponse toVehiclePhotoResponse(VehiclePhoto photo, FileMetadata metadata) {
        Signed signed = buildSignedUrl(metadata);
        return new VehiclePhotoResponse(
                photo.getId(),
                photo.getVehicleId(),
                photo.getFileId(),
                photo.isPrimary(),
                photo.getDisplayOrder(),
                metadata.getVisibility().name(),
                signed.url(),
                signed.expiresAt());
    }

    private ListingPhotoResponse toListingPhotoResponse(ListingPhoto photo) {
        FileMetadata metadata = fileMetadataRepository.findByIdAndStatus(photo.getFileId(), FileStatus.ACTIVE)
                .orElseThrow(() -> new ResourceNotFoundException("FILE_NOT_FOUND", "File", photo.getFileId().toString()));
        return toListingPhotoResponse(photo, metadata);
    }

    private ListingPhotoResponse toListingPhotoResponse(ListingPhoto photo, FileMetadata metadata) {
        Signed signed = buildSignedUrl(metadata);
        return new ListingPhotoResponse(
                photo.getId(),
                photo.getListingId(),
                photo.getFileId(),
                photo.isPrimary(),
                photo.getDisplayOrder(),
                metadata.getVisibility().name(),
                signed.url(),
                signed.expiresAt());
    }

    private String signedUrlForPhoto(ListingPhoto photo) {
        FileMetadata metadata = fileMetadataRepository.findByIdAndStatus(photo.getFileId(), FileStatus.ACTIVE)
                .orElseThrow(() -> new ResourceNotFoundException("FILE_NOT_FOUND", "File", photo.getFileId().toString()));
        return buildSignedUrl(metadata).url();
    }

    private String signedUrlForVehiclePhoto(VehiclePhoto photo) {
        FileMetadata metadata = fileMetadataRepository.findByIdAndStatus(photo.getFileId(), FileStatus.ACTIVE)
                .orElseThrow(() -> new ResourceNotFoundException("FILE_NOT_FOUND", "File", photo.getFileId().toString()));
        return buildSignedUrl(metadata).url();
    }

    private Comparator<ListingPhoto> photoComparator() {
        return Comparator.comparing(ListingPhoto::isPrimary).reversed()
                .thenComparing(ListingPhoto::getDisplayOrder);
    }

    private Comparator<VehiclePhoto> vehiclePhotoComparator() {
        return Comparator.comparing(VehiclePhoto::isPrimary).reversed()
                .thenComparing(VehiclePhoto::getDisplayOrder);
    }

    private void assertReadable(FileMetadata file) {
        if (file.getVisibility() == FileVisibility.PUBLIC) {
            return;
        }
        UUID currentUser = securityContext.currentUserId();
        if (securityContext.hasRole(Role.ADMIN) || file.getOwnerUserId().equals(currentUser)) {
            return;
        }
        throw new AccessDeniedException();
    }

    private Signed buildSignedUrl(FileMetadata metadata) {
        return buildSignedUrl(metadata, "read");
    }

    private Signed buildSignedUrl(FileMetadata metadata, String action) {
        if ("read".equals(action)
                && metadata.getVisibility() == FileVisibility.PUBLIC
                && metadata.getStatus() == FileStatus.ACTIVE
                && metadata.getExternalUrl() != null
                && !metadata.getExternalUrl().isBlank()) {
            return new Signed(metadata.getExternalUrl().trim(), Instant.now().plus(signedUrlProperties.getTtl()));
        }
        Instant expiresAt = Instant.now().plus(signedUrlProperties.getTtl());
        long expiresAtEpoch = expiresAt.getEpochSecond();
        String payload = action + ":" + metadata.getId() + ":" + metadata.getBucket() + ":" + metadata.getObjectKey() + ":" + expiresAtEpoch;
        String signature = hmacSha256(payload, signedUrlProperties.getSecret());
        String baseUrl = signedUrlProperties.getBaseUrl().replaceAll("/+$", "");
        String url = baseUrl + "/files/" + metadata.getId()
                + "?bucket=" + encode(metadata.getBucket())
                + "&key=" + encode(metadata.getObjectKey())
                + "&action=" + encode(action)
                + "&exp=" + expiresAtEpoch
                + "&sig=" + encode(signature);
        return new Signed(url, expiresAt);
    }

    private String hmacSha256(String payload, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to generate signed url", e);
        }
    }

    private String encode(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private record Signed(String url, Instant expiresAt) {
    }
}
