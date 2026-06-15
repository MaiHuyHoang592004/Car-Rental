package com.rentflow.integration.trip;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rentflow.auth.entity.AuthUser;
import com.rentflow.auth.entity.Role;
import com.rentflow.auth.entity.UserStatus;
import com.rentflow.auth.repository.AuthUserRepository;
import com.rentflow.booking.entity.Booking;
import com.rentflow.booking.entity.BookingStatus;
import com.rentflow.booking.repository.BookingRepository;
import com.rentflow.common.security.JwtTokenProvider;
import com.rentflow.integration.BaseIntegrationTest;
import com.rentflow.listing.entity.CancellationPolicy;
import com.rentflow.listing.entity.Listing;
import com.rentflow.listing.entity.ListingStatus;
import com.rentflow.listing.repository.ListingRepository;
import com.rentflow.payment.entity.BookingPayment;
import com.rentflow.payment.entity.PaymentMethod;
import com.rentflow.payment.entity.PaymentProviderType;
import com.rentflow.payment.entity.PaymentStatus;
import com.rentflow.payment.entity.PaymentTransactionType;
import com.rentflow.payment.provider.corebank.CoreBankCaptureHoldResponse;
import com.rentflow.payment.provider.corebank.CoreBankCaptureHoldResult;
import com.rentflow.payment.provider.corebank.CoreBankPaymentClient;
import com.rentflow.payment.repository.BookingPaymentRepository;
import com.rentflow.payment.repository.PaymentTransactionRepository;
import com.rentflow.trip.repository.TripRecordRepository;
import com.rentflow.tripcondition.repository.TripConditionPhotoRepository;
import com.rentflow.tripcondition.repository.TripConditionReportRepository;
import com.rentflow.tripcondition.repository.TripDamageItemRepository;
import com.rentflow.vehicle.entity.FuelType;
import com.rentflow.vehicle.entity.TransmissionType;
import com.rentflow.vehicle.entity.Vehicle;
import com.rentflow.vehicle.entity.VehicleCategory;
import com.rentflow.vehicle.entity.VehicleStatus;
import com.rentflow.vehicle.repository.VehicleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Tag("integration")
class TripLifecycleIntegrationTest extends BaseIntegrationTest {

    @Autowired private AuthUserRepository authUserRepository;
    @Autowired private VehicleRepository vehicleRepository;
    @Autowired private ListingRepository listingRepository;
    @Autowired private BookingRepository bookingRepository;
    @Autowired private BookingPaymentRepository bookingPaymentRepository;
    @Autowired private PaymentTransactionRepository paymentTransactionRepository;
    @Autowired private TripRecordRepository tripRecordRepository;
    @Autowired private TripConditionReportRepository conditionReportRepository;
    @Autowired private TripConditionPhotoRepository conditionPhotoRepository;
    @Autowired private TripDamageItemRepository damageItemRepository;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired private ObjectMapper objectMapper;
    @MockBean private CoreBankPaymentClient coreBankPaymentClient;

    private AuthUser host;
    private AuthUser customer;
    private String customerToken;
    private Listing listing;

    @BeforeEach
    void setUp() {
        paymentTransactionRepository.deleteAll();
        bookingPaymentRepository.deleteAll();
        damageItemRepository.deleteAll();
        conditionPhotoRepository.deleteAll();
        conditionReportRepository.deleteAll();
        tripRecordRepository.deleteAll();
        bookingRepository.deleteAll();
        listingRepository.deleteAll();
        vehicleRepository.deleteAll();
        authUserRepository.deleteAll();

        host = saveUser("host-" + UUID.randomUUID() + "@example.com", Role.HOST);
        customer = saveUser("customer-" + UUID.randomUUID() + "@example.com", Role.CUSTOMER);
        customerToken = jwtTokenProvider.generateAccessToken(customer.getId(), customer.getEmail(), List.of(Role.CUSTOMER));
        listing = saveListing(host, ListingStatus.ACTIVE);
    }

    @Test
    void checkInThenCheckOutTransitionsBookingAndCapturesPayment() throws Exception {
        Booking booking = saveBooking(customer.getId(), host.getId(), listing.getId(), BookingStatus.CONFIRMED);
        BookingPayment payment = saveAuthorizedPayment(booking.getId());
        when(coreBankPaymentClient.captureHold(any())).thenReturn(new CoreBankCaptureHoldResult(
                new CoreBankCaptureHoldResponse("payment-order-1", "journal-1", "CAPTURED"),
                "{\"paymentOrderId\":\"payment-order-1\",\"journalId\":\"journal-1\",\"status\":\"CAPTURED\"}"));

        submitConditionReport(booking.getId(), customerToken, "CHECK_IN", 15000, 80);
        mockMvc.perform(post("/api/v1/bookings/{id}/check-in", booking.getId())
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "odometer":15000,
                                  "fuelLevel":80,
                                  "note":"start"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookingStatus").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.checkInOdometer").value(15000));

        submitConditionReport(booking.getId(), customerToken, "CHECK_OUT", 15220, 70);
        mockMvc.perform(post("/api/v1/bookings/{id}/check-out", booking.getId())
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "odometer":15220,
                                  "fuelLevel":70,
                                  "note":"end"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookingStatus").value("COMPLETED"))
                .andExpect(jsonPath("$.checkOutOdometer").value(15220));

        Booking updatedBooking = bookingRepository.findById(booking.getId()).orElseThrow();
        assertThat(updatedBooking.getStatus()).isEqualTo(BookingStatus.COMPLETED);

        BookingPayment updatedPayment = bookingPaymentRepository.findById(payment.getId()).orElseThrow();
        assertThat(updatedPayment.getStatus()).isEqualTo(PaymentStatus.CAPTURED);
        assertThat(updatedPayment.getCapturedAmount()).isEqualByComparingTo("1400000.00");
        assertThat(paymentTransactionRepository.findByBookingPaymentIdOrderByCreatedAtAsc(payment.getId()))
                .anySatisfy(tx -> assertThat(tx.getType()).isEqualTo(PaymentTransactionType.CAPTURE));
    }

    @Test
    void checkInRequiresMatchingConditionReport() throws Exception {
        Booking booking = saveBooking(customer.getId(), host.getId(), listing.getId(), BookingStatus.CONFIRMED);

        mockMvc.perform(post("/api/v1/bookings/{id}/check-in", booking.getId())
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "odometer":15000,
                                  "fuelLevel":80,
                                  "note":"start"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TRIP_CONDITION_REPORT_REQUIRED"));
    }

    @Test
    void unrelatedUserCannotViewConditionReports() throws Exception {
        Booking booking = saveBooking(customer.getId(), host.getId(), listing.getId(), BookingStatus.CONFIRMED);
        submitConditionReport(booking.getId(), customerToken, "CHECK_IN", 15000, 80);
        AuthUser unrelated = saveUser("unrelated-" + UUID.randomUUID() + "@example.com", Role.CUSTOMER);
        String unrelatedToken = jwtTokenProvider.generateAccessToken(
                unrelated.getId(),
                unrelated.getEmail(),
                List.of(Role.CUSTOMER));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                                "/api/v1/bookings/{id}/condition-reports", booking.getId())
                        .header("Authorization", "Bearer " + unrelatedToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void duplicateConditionReportIsRejected() throws Exception {
        Booking booking = saveBooking(customer.getId(), host.getId(), listing.getId(), BookingStatus.CONFIRMED);
        submitConditionReport(booking.getId(), customerToken, "CHECK_IN", 15000, 80);

        mockMvc.perform(post("/api/v1/bookings/{id}/condition-reports", booking.getId())
                        .header("Authorization", "Bearer " + customerToken)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(conditionReportJson("CHECK_IN", 15000, 80, createTripPhotoFiles(booking.getId(), customerToken))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TRIP_CONDITION_REPORT_ALREADY_EXISTS"));
    }

    @Test
    void checkoutConditionReportRejectsLowerOdometerThanCheckin() throws Exception {
        Booking booking = saveBooking(customer.getId(), host.getId(), listing.getId(), BookingStatus.CONFIRMED);
        submitConditionReport(booking.getId(), customerToken, "CHECK_IN", 15000, 80);
        mockMvc.perform(post("/api/v1/bookings/{id}/check-in", booking.getId())
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "odometer":15000,
                                  "fuelLevel":80,
                                  "note":"start"
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/bookings/{id}/condition-reports", booking.getId())
                        .header("Authorization", "Bearer " + customerToken)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(conditionReportJson("CHECK_OUT", 14999, 70, createTripPhotoFiles(booking.getId(), customerToken))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    private AuthUser saveUser(String email, Role role) {
        AuthUser user = new AuthUser(email, "hash", UserStatus.ACTIVE, true);
        user.addRole(role);
        return authUserRepository.save(user);
    }

    private Listing saveListing(AuthUser hostUser, ListingStatus status) {
        Vehicle vehicle = new Vehicle();
        vehicle.setHostId(hostUser.getId());
        vehicle.setCategory(VehicleCategory.SEDAN);
        vehicle.setMake("Toyota");
        vehicle.setModel("Vios");
        vehicle.setManufactureYear(2022);
        vehicle.setTransmission(TransmissionType.AUTO);
        vehicle.setFuelType(FuelType.PETROL);
        vehicle.setSeats(5);
        vehicle.setCity("Hanoi");
        vehicle.setStatus(VehicleStatus.ACTIVE);
        vehicle = vehicleRepository.save(vehicle);

        Listing newListing = new Listing();
        newListing.setHostId(hostUser.getId());
        newListing.setVehicleId(vehicle.getId());
        newListing.setTitle("Trip listing");
        newListing.setCity("Hanoi");
        newListing.setBasePricePerDay(new BigDecimal("700000.00"));
        newListing.setCurrency("VND");
        newListing.setInstantBook(true);
        newListing.setDailyKmLimit(200);
        newListing.setCancellationPolicy(CancellationPolicy.FLEXIBLE);
        newListing.setStatus(status);
        return listingRepository.save(newListing);
    }

    private Booking saveBooking(UUID customerId, UUID hostId, UUID listingId, BookingStatus status) {
        Booking booking = new Booking();
        booking.setCustomerId(customerId);
        booking.setHostId(hostId);
        booking.setListingId(listingId);
        booking.setPickupDate(LocalDate.of(2026, 6, 1));
        booking.setReturnDate(LocalDate.of(2026, 6, 3));
        booking.setStatus(status);
        booking.setPriceSnapshot("""
                {"totalAmount":1400000.00,"currency":"VND","extras":[]}
                """);
        booking.setPolicySnapshot("""
                {"cancellationPolicy":"FLEXIBLE","instantBook":true,"dailyKmLimit":200}
                """);
        return bookingRepository.save(booking);
    }

    private BookingPayment saveAuthorizedPayment(UUID bookingId) {
        BookingPayment payment = new BookingPayment();
        payment.setBookingId(bookingId);
        payment.setSelectedBankId(UUID.fromString("00000000-0000-0000-0000-000000000111"));
        payment.setPaymentMethod(PaymentMethod.COREBANK_TRANSFER);
        payment.setProvider(PaymentProviderType.COREBANK);
        payment.setStatus(PaymentStatus.AUTHORIZED);
        payment.setAuthorizedAmount(new BigDecimal("1400000.00"));
        payment.setCapturedAmount(BigDecimal.ZERO);
        payment.setRefundedAmount(BigDecimal.ZERO);
        payment.setCurrency("VND");
        payment.setExternalOrderRef("rentflow:booking:" + bookingId);
        payment.setProviderPaymentOrderId("payment-order-1");
        payment.setProviderHoldId("hold-1");
        payment.setProviderStatus("AUTHORIZED");
        return bookingPaymentRepository.save(payment);
    }

    private void submitConditionReport(
            UUID bookingId,
            String token,
            String reportType,
            int odometer,
            int fuelLevel) throws Exception {
        mockMvc.perform(post("/api/v1/bookings/{id}/condition-reports", bookingId)
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(conditionReportJson(reportType, odometer, fuelLevel, createTripPhotoFiles(bookingId, token))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reportType").value(reportType))
                .andExpect(jsonPath("$.photos").isArray())
                .andExpect(jsonPath("$.photos.length()").value(4));
    }

    private List<UUID> createTripPhotoFiles(UUID bookingId, String token) throws Exception {
        List<UUID> fileIds = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            JsonNode uploadIntent = objectMapper.readTree(mockMvc.perform(post(
                                    "/api/v1/bookings/{id}/trip-photos/upload-intent", bookingId)
                            .header("Authorization", "Bearer " + token)
                            .header("Idempotency-Key", UUID.randomUUID().toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "contentType":"image/jpeg",
                                      "sizeBytes":1024
                                    }
                                    """))
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse()
                    .getContentAsString());
            UUID fileId = UUID.fromString(uploadIntent.get("fileId").asText());
            mockMvc.perform(post("/api/v1/files/{fileId}/finalize", fileId)
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk());
            fileIds.add(fileId);
        }
        return fileIds;
    }

    private String conditionReportJson(
            String reportType,
            int odometer,
            int fuelLevel,
            List<UUID> fileIds) {
        return """
                {
                  "reportType":"%s",
                  "odometer":%d,
                  "fuelLevel":%d,
                  "hasVisibleDamage":false,
                  "photos":[
                    {"fileId":"%s","angle":"FRONT","displayOrder":0},
                    {"fileId":"%s","angle":"REAR","displayOrder":1},
                    {"fileId":"%s","angle":"LEFT","displayOrder":2},
                    {"fileId":"%s","angle":"RIGHT","displayOrder":3}
                  ],
                  "damageItems":[]
                }
                """.formatted(reportType, odometer, fuelLevel, fileIds.get(0), fileIds.get(1), fileIds.get(2), fileIds.get(3));
    }
}
