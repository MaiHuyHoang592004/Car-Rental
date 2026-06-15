package com.rentflow.demo;

import com.rentflow.auth.entity.AuthUser;
import com.rentflow.auth.entity.Role;
import com.rentflow.auth.entity.UserRole;
import com.rentflow.auth.entity.UserStatus;
import com.rentflow.auth.repository.AuthUserRepository;
import com.rentflow.auth.repository.UserRoleRepository;
import com.rentflow.availability.repository.AvailabilityCalendarRepository;
import com.rentflow.file.entity.FileMetadata;
import com.rentflow.file.entity.FilePurpose;
import com.rentflow.file.entity.FileStatus;
import com.rentflow.file.entity.FileVisibility;
import com.rentflow.file.entity.ListingPhoto;
import com.rentflow.file.repository.FileMetadataRepository;
import com.rentflow.file.repository.ListingPhotoRepository;
import com.rentflow.listing.entity.CancellationPolicy;
import com.rentflow.listing.entity.Extra;
import com.rentflow.listing.entity.Listing;
import com.rentflow.listing.entity.ListingStatus;
import com.rentflow.listing.entity.PricingType;
import com.rentflow.listing.repository.ListingRepository;
import com.rentflow.notification.entity.Notification;
import com.rentflow.notification.entity.NotificationDeliveryStatus;
import com.rentflow.notification.entity.NotificationType;
import com.rentflow.notification.repository.NotificationRepository;
import com.rentflow.user.entity.UserProfile;
import com.rentflow.user.repository.UserProfileRepository;
import com.rentflow.vehicle.entity.FuelType;
import com.rentflow.vehicle.entity.TransmissionType;
import com.rentflow.vehicle.entity.Vehicle;
import com.rentflow.vehicle.entity.VehicleCategory;
import com.rentflow.vehicle.entity.VehicleStatus;
import com.rentflow.vehicle.repository.VehicleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "rentflow.demo.seed.enabled", havingValue = "true")
public class DemoDataSeeder implements ApplicationRunner {

    private static final String DEMO_HOST_EMAIL = "demo-host@rentflow.local";
    private static final String DEMO_CUSTOMER_EMAIL = "demo-customer@rentflow.local";
    private static final String DEMO_CUSTOMER_PASSWORD = "RentFlowDemo!2026";
    private static final String EXTERNAL_ASSET_BUCKET = "external-demo-assets";
    private static final int AVAILABILITY_DAYS = 365;

    private static final List<DemoListing> DEMO_LISTINGS = List.of(
            new DemoListing(
                    "Toyota Vios 2022",
                    "Toyota",
                    "Vios",
                    2022,
                    VehicleCategory.SEDAN,
                    FuelType.PETROL,
                    5,
                    "Ho Chi Minh City",
                    "District 1, Ho Chi Minh City",
                    "Reliable city sedan for airport transfers, meetings, and weekend trips.",
                    "700000",
                    200,
                    false,
                    CancellationPolicy.FLEXIBLE,
                    "4.8",
                    138,
                    "https://images.unsplash.com/photo-1552519507-da3b142c6e3d?auto=format&fit=crop&w=1200&q=80",
                    List.of(extra("Child Seat", "120000", PricingType.PER_DAY), extra("Airport Delivery", "250000", PricingType.PER_TRIP))),
            new DemoListing(
                    "Hyundai Santa Fe 2023",
                    "Hyundai",
                    "Santa Fe",
                    2023,
                    VehicleCategory.SUV,
                    FuelType.DIESEL,
                    7,
                    "Da Nang",
                    "Hai Chau District, Da Nang",
                    "Spacious SUV with premium safety features for family and coastal routes.",
                    "1250000",
                    250,
                    true,
                    CancellationPolicy.MODERATE,
                    "4.7",
                    91,
                    "https://images.unsplash.com/photo-1619767886558-efdc259cde1a?auto=format&fit=crop&w=1200&q=80",
                    List.of(extra("Camping Kit", "180000", PricingType.PER_TRIP), extra("Baby Seat", "100000", PricingType.PER_DAY))),
            new DemoListing(
                    "VinFast VF 8 Plus 2024",
                    "VinFast",
                    "VF 8 Plus",
                    2024,
                    VehicleCategory.SUV,
                    FuelType.EV,
                    5,
                    "Hanoi",
                    "Tay Ho District, Hanoi",
                    "Electric SUV for quiet city drives and premium weekend escapes.",
                    "1550000",
                    240,
                    true,
                    CancellationPolicy.FLEXIBLE,
                    "4.9",
                    64,
                    "https://images.unsplash.com/photo-1542282088-fe8426682b8f?auto=format&fit=crop&w=1200&q=80",
                    List.of(extra("Portable Charger", "150000", PricingType.PER_TRIP))),
            new DemoListing(
                    "Kia Carnival 2022",
                    "Kia",
                    "Carnival",
                    2022,
                    VehicleCategory.MPV,
                    FuelType.DIESEL,
                    7,
                    "Hanoi",
                    "Cau Giay District, Hanoi",
                    "Comfortable MPV with roomy captain seats for group and family travel.",
                    "1350000",
                    260,
                    false,
                    CancellationPolicy.MODERATE,
                    "4.8",
                    77,
                    "https://images.unsplash.com/photo-1492144534655-ae79c964c9d7?auto=format&fit=crop&w=1200&q=80",
                    List.of(extra("Extra Luggage Rack", "90000", PricingType.PER_DAY))),
            new DemoListing(
                    "Mazda 3 2023",
                    "Mazda",
                    "3",
                    2023,
                    VehicleCategory.SEDAN,
                    FuelType.PETROL,
                    5,
                    "Ho Chi Minh City",
                    "District 7, Ho Chi Minh City",
                    "Compact premium sedan with polished handling for urban renters.",
                    "900000",
                    220,
                    true,
                    CancellationPolicy.FLEXIBLE,
                    "4.6",
                    52,
                    "https://images.unsplash.com/photo-1503376780353-7e6692767b70?auto=format&fit=crop&w=1200&q=80",
                    List.of(extra("Phone Mount", "50000", PricingType.PER_TRIP))),
            new DemoListing(
                    "Ford Ranger Wildtrak 2021",
                    "Ford",
                    "Ranger Wildtrak",
                    2021,
                    VehicleCategory.PICKUP,
                    FuelType.DIESEL,
                    5,
                    "Da Nang",
                    "Son Tra District, Da Nang",
                    "Pickup truck for mountain roads, beach gear, and weekend logistics.",
                    "1150000",
                    280,
                    false,
                    CancellationPolicy.STRICT,
                    "4.7",
                    46,
                    "https://images.unsplash.com/photo-1549924231-f129b911e442?auto=format&fit=crop&w=1200&q=80",
                    List.of(extra("Roof Box", "120000", PricingType.PER_DAY)))
    );

    private static final List<DemoNotification> DEMO_NOTIFICATIONS = List.of(
            new DemoNotification(
                    DemoAudience.CUSTOMER,
                    NotificationType.DRIVER_VERIFICATION_EXPIRED,
                    "Giay phep lai xe sap het han",
                    "Ho so xac minh tai xe cua ban can cap nhat truoc khi dat chuyen tiep theo.",
                    false),
            new DemoNotification(
                    DemoAudience.CUSTOMER,
                    NotificationType.SUPPORT_CASE_MESSAGE,
                    "Ho tro da phan hoi yeu cau",
                    "RentFlow da gui huong dan dieu chinh ngay nhan xe cho dat cho sap toi.",
                    false),
            new DemoNotification(
                    DemoAudience.HOST,
                    NotificationType.LISTING_REJECTED,
                    "Mazda 3 can bo sung anh dang ky",
                    "Tin dang can them anh dang ky xe ro net truoc khi duoc duyet cong khai.",
                    false),
            new DemoNotification(
                    DemoAudience.HOST,
                    NotificationType.HOST_PAYOUT_UPDATED,
                    "Payout cuoi tuan da duoc cap nhat",
                    "Khoan thanh toan cho cac chuyen da hoan tat dang o trang thai doi chuyen khoan.",
                    true)
    );

    private final DemoDataProperties properties;
    private final AuthUserRepository authUserRepository;
    private final UserRoleRepository userRoleRepository;
    private final UserProfileRepository userProfileRepository;
    private final VehicleRepository vehicleRepository;
    private final ListingRepository listingRepository;
    private final AvailabilityCalendarRepository availabilityRepository;
    private final FileMetadataRepository fileMetadataRepository;
    private final ListingPhotoRepository listingPhotoRepository;
    private final NotificationRepository notificationRepository;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!properties.isEnabled()) {
            return;
        }

        AuthUser host = ensureDemoHost();
        AuthUser customer = ensureDemoCustomer();
        int created = 0;
        for (DemoListing sample : DEMO_LISTINGS) {
            Listing listing = listingRepository.findByTitle(sample.title())
                    .orElseGet(() -> {
                        Vehicle vehicle = createVehicle(host.getId(), sample);
                        return createListing(host.getId(), vehicle.getId(), sample);
                    });
            ensureAvailability(listing.getId());
            ensurePhoto(host.getId(), listing.getId(), sample);
            created++;
        }
        int notifications = ensureDemoNotifications(host.getId(), customer.getId());
        log.info("RentFlow demo seed verified {} public listings and {} notifications", created, notifications);
    }

    private AuthUser ensureDemoHost() {
        AuthUser host = authUserRepository.findByEmail(DEMO_HOST_EMAIL)
                .orElseGet(() -> {
                    AuthUser user = new AuthUser(
                            DEMO_HOST_EMAIL,
                            passwordEncoder.encode(UUID.randomUUID().toString()),
                            UserStatus.ACTIVE,
                            true);
                    return authUserRepository.save(user);
                });

        if (!userRoleRepository.existsByUserIdAndRole(host.getId(), Role.HOST)) {
            userRoleRepository.save(new UserRole(host, Role.HOST));
        }
        if (userProfileRepository.findByUserId(host.getId()).isEmpty()) {
            UserProfile profile = new UserProfile("RentFlow Demo Host");
            profile.setUser(host);
            userProfileRepository.save(profile);
        }
        return host;
    }

    private AuthUser ensureDemoCustomer() {
        AuthUser customer = authUserRepository.findByEmail(DEMO_CUSTOMER_EMAIL)
                .orElseGet(() -> {
                    AuthUser user = new AuthUser(
                            DEMO_CUSTOMER_EMAIL,
                            passwordEncoder.encode(DEMO_CUSTOMER_PASSWORD),
                            UserStatus.ACTIVE,
                            true);
                    return authUserRepository.save(user);
                });

        if (!userRoleRepository.existsByUserIdAndRole(customer.getId(), Role.CUSTOMER)) {
            userRoleRepository.save(new UserRole(customer, Role.CUSTOMER));
        }
        if (userProfileRepository.findByUserId(customer.getId()).isEmpty()) {
            UserProfile profile = new UserProfile("RentFlow Demo Customer");
            profile.setUser(customer);
            userProfileRepository.save(profile);
        }
        return customer;
    }

    private int ensureDemoNotifications(UUID hostId, UUID customerId) {
        int created = 0;
        for (DemoNotification sample : DEMO_NOTIFICATIONS) {
            UUID userId = sample.audience() == DemoAudience.HOST ? hostId : customerId;
            if (notificationRepository.findByUserIdAndTypeAndTitle(userId, sample.type(), sample.title()).isPresent()) {
                continue;
            }
            Notification notification = new Notification();
            notification.setUserId(userId);
            notification.setType(sample.type());
            notification.setTitle(sample.title());
            notification.setMessage(sample.message());
            notification.setDeliveryStatus(NotificationDeliveryStatus.SENT);
            if (sample.read()) {
                notification.setReadAt(clock.instant());
            }
            notificationRepository.save(notification);
            created++;
        }
        return created;
    }

    private Vehicle createVehicle(UUID hostId, DemoListing sample) {
        Vehicle vehicle = new Vehicle();
        vehicle.setHostId(hostId);
        vehicle.setCategory(sample.category());
        vehicle.setMake(sample.make());
        vehicle.setModel(sample.model());
        vehicle.setManufactureYear(sample.year());
        vehicle.setTransmission(TransmissionType.AUTO);
        vehicle.setFuelType(sample.fuelType());
        vehicle.setSeats(sample.seats());
        vehicle.setStatus(VehicleStatus.ACTIVE);
        vehicle.setCity(sample.city());
        return vehicleRepository.save(vehicle);
    }

    private Listing createListing(UUID hostId, UUID vehicleId, DemoListing sample) {
        Listing listing = new Listing();
        listing.setHostId(hostId);
        listing.setVehicleId(vehicleId);
        listing.setTitle(sample.title());
        listing.setDescription(sample.description());
        listing.setCity(sample.city());
        listing.setAddress(sample.address());
        listing.setBasePricePerDay(new BigDecimal(sample.pricePerDay()));
        listing.setCurrency("VND");
        listing.setDailyKmLimit(sample.dailyKmLimit());
        listing.setInstantBook(sample.instantBook());
        listing.setCancellationPolicy(sample.cancellationPolicy());
        listing.setAverageRating(new BigDecimal(sample.averageRating()));
        listing.setReviewCount(sample.reviewCount());
        listing.setStatus(ListingStatus.ACTIVE);
        for (DemoExtra sampleExtra : sample.extras()) {
            Extra extra = new Extra();
            extra.setListing(listing);
            extra.setName(sampleExtra.name());
            extra.setPricingType(sampleExtra.pricingType());
            extra.setPrice(new BigDecimal(sampleExtra.price()));
            extra.setActive(true);
            listing.getExtras().add(extra);
        }
        return listingRepository.save(listing);
    }

    private void ensureAvailability(UUID listingId) {
        if (availabilityRepository.countByListingId(listingId) > 0) {
            return;
        }
        LocalDate today = LocalDate.now(clock);
        availabilityRepository.insertAvailabilityRange(listingId, today, today.plusDays(AVAILABILITY_DAYS - 1));
    }

    private void ensurePhoto(UUID hostId, UUID listingId, DemoListing sample) {
        if (listingPhotoRepository.countByListingId(listingId) > 0) {
            return;
        }
        String objectKey = "demo/listings/" + slug(sample.title()) + "-" + listingId + ".jpg";
        FileMetadata file = fileMetadataRepository.findByBucketAndObjectKey(EXTERNAL_ASSET_BUCKET, objectKey)
                .orElseGet(() -> createExternalPhoto(hostId, objectKey, sample.imageUrl()));

        ListingPhoto photo = new ListingPhoto();
        photo.setListingId(listingId);
        photo.setFileId(file.getId());
        photo.setDisplayOrder(0);
        photo.setPrimary(true);
        listingPhotoRepository.save(photo);
    }

    private FileMetadata createExternalPhoto(UUID hostId, String objectKey, String imageUrl) {
        FileMetadata file = new FileMetadata();
        file.setOwnerUserId(hostId);
        file.setPurpose(FilePurpose.LISTING_PHOTO);
        file.setBucket(EXTERNAL_ASSET_BUCKET);
        file.setObjectKey(objectKey);
        file.setExternalUrl(imageUrl);
        file.setContentType("image/jpeg");
        file.setSizeBytes(1L);
        file.setVisibility(FileVisibility.PUBLIC);
        file.setStatus(FileStatus.ACTIVE);
        return fileMetadataRepository.save(file);
    }

    private static String slug(String value) {
        return value.toLowerCase()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
    }

    private static DemoExtra extra(String name, String price, PricingType pricingType) {
        return new DemoExtra(name, price, pricingType);
    }

    private record DemoListing(
            String title,
            String make,
            String model,
            int year,
            VehicleCategory category,
            FuelType fuelType,
            int seats,
            String city,
            String address,
            String description,
            String pricePerDay,
            int dailyKmLimit,
            boolean instantBook,
            CancellationPolicy cancellationPolicy,
            String averageRating,
            int reviewCount,
            String imageUrl,
            List<DemoExtra> extras) {
    }

    private record DemoExtra(String name, String price, PricingType pricingType) {
    }

    private enum DemoAudience {
        HOST,
        CUSTOMER
    }

    private record DemoNotification(
            DemoAudience audience,
            NotificationType type,
            String title,
            String message,
            boolean read) {
    }
}
