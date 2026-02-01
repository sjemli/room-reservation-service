package com.marvel.hospitality.reservationservice.dto;


import com.marvel.hospitality.reservationservice.model.PaymentMode;
import com.marvel.hospitality.reservationservice.model.RoomSegment;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;


import java.time.LocalDate;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

public record ReservationRequest(

        @NotBlank(message = "Customer name is required")
        @Size(min = 2, max = 100, message = "Customer name must be between 2 and 100 characters")
        @Schema(description = "Full name of the customer", example = "John Doe", requiredMode = REQUIRED)
        String customerName,

        @NotBlank(message = "Room number is required")
        @Size(min = 1, max = 10, message = "Room number must be between 1 and 10 characters")
        @Schema(description = "Hotel room number", example = "101", requiredMode = REQUIRED)
        String roomNumber,

        @NotNull(message = "Start date is required")
        @FutureOrPresent(message = "Start date must be today or in the future")
        @Schema(description = "Reservation start date", example = "2026-03-01", requiredMode = REQUIRED)
        LocalDate startDate,

        @NotNull(message = "End date is required")
        @Future(message = "End date must be in the future")
        @Schema(description = "Reservation end date (must be after start date)", example = "2026-03-05",
                requiredMode = REQUIRED)
        LocalDate endDate,

        @NotNull(message = "Room segment is required")
        @Schema(description = "Room size category",
                allowableValues = {"SMALL", "MEDIUM", "LARGE", "EXTRA_LARGE"}, requiredMode = REQUIRED)
        RoomSegment segment,

        @NotNull(message = "Payment mode is required")
        @Schema(description = "Payment method", example = "CREDIT_CARD",
                allowableValues = {"CASH", "BANK_TRANSFER", "CREDIT_CARD"}, requiredMode = REQUIRED)
        PaymentMode paymentMode,

        @Schema(description = "Payment reference number (required for CREDIT_CARD, optional otherwise)", example = "PAYREF-123456")
        String paymentReference
) {}