package com.rentflow.tripcondition.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.rentflow.common.exception.CorrelationIdHelper;
import com.rentflow.common.exception.GlobalExceptionHandler;
import com.rentflow.file.dto.CreatePhotoUploadIntentRequest;
import com.rentflow.file.dto.FileUploadIntentResponse;
import com.rentflow.tripcondition.dto.TripConditionReportResponse;
import com.rentflow.tripcondition.entity.TripConditionReportType;
import com.rentflow.tripcondition.service.TripConditionReportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TripConditionReportControllerTest {

    private static final String VALID_IDEMPOTENCY_KEY = "8b71f8d2-9e1d-4f7a-bbe6-334c3816df91";

    private TripConditionReportService service;
    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        service = mock(TripConditionReportService.class);
        objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mockMvc = MockMvcBuilders.standaloneSetup(new TripConditionReportController(service))
                .setControllerAdvice(new GlobalExceptionHandler(new CorrelationIdHelper()))
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    @Test
    void createReportMissingIdempotencyKeyReturnsRequiredError() throws Exception {
        UUID bookingId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/bookings/{bookingId}/condition-reports", bookingId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validReportBody()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REQUIRED"));
    }

    @Test
    void createTripPhotoUploadIntentInvalidIdempotencyKeyReturnsValidationError() throws Exception {
        UUID bookingId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/bookings/{bookingId}/trip-photos/upload-intent", bookingId)
                        .header("Idempotency-Key", "not-a-v4-uuid")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreatePhotoUploadIntentRequest(
                                "image/jpeg",
                                1024L,
                                null))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void createTripPhotoUploadIntentDelegates() throws Exception {
        UUID bookingId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        when(service.createTripPhotoUploadIntent(eq(bookingId), eq(VALID_IDEMPOTENCY_KEY), any()))
                .thenReturn(new FileUploadIntentResponse(
                        fileId,
                        "rentflow-trip-photos",
                        "trips/" + bookingId + "/file",
                        "https://files.local/upload",
                        Instant.parse("2026-06-01T00:10:00Z")));

        mockMvc.perform(post("/api/v1/bookings/{bookingId}/trip-photos/upload-intent", bookingId)
                        .header("Idempotency-Key", VALID_IDEMPOTENCY_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreatePhotoUploadIntentRequest(
                                "image/jpeg",
                                1024L,
                                null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fileId").value(fileId.toString()))
                .andExpect(jsonPath("$.bucket").value("rentflow-trip-photos"));

        verify(service).createTripPhotoUploadIntent(eq(bookingId), eq(VALID_IDEMPOTENCY_KEY), any());
    }

    @Test
    void listReportsDelegates() throws Exception {
        UUID bookingId = UUID.randomUUID();
        UUID reportId = UUID.randomUUID();
        when(service.listReports(bookingId)).thenReturn(List.of(response(reportId, bookingId)));

        mockMvc.perform(get("/api/v1/bookings/{bookingId}/condition-reports", bookingId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(reportId.toString()))
                .andExpect(jsonPath("$[0].reportType").value("CHECK_IN"));
    }

    private String validReportBody() {
        return """
                {
                  "reportType":"CHECK_IN",
                  "odometer":15000,
                  "fuelLevel":80,
                  "photos":[
                    {"fileId":"11111111-1111-4111-8111-111111111111","angle":"FRONT"},
                    {"fileId":"22222222-2222-4222-8222-222222222222","angle":"REAR"},
                    {"fileId":"33333333-3333-4333-8333-333333333333","angle":"LEFT"},
                    {"fileId":"44444444-4444-4444-8444-444444444444","angle":"RIGHT"}
                  ],
                  "damageItems":[]
                }
                """;
    }

    private TripConditionReportResponse response(UUID reportId, UUID bookingId) {
        return new TripConditionReportResponse(
                reportId,
                bookingId,
                null,
                UUID.randomUUID(),
                com.rentflow.tripcondition.entity.TripConditionReporterRole.CUSTOMER,
                TripConditionReportType.CHECK_IN,
                15000,
                80,
                null,
                null,
                false,
                null,
                null,
                null,
                Instant.parse("2026-06-01T00:00:00Z"),
                List.of(),
                List.of());
    }
}
