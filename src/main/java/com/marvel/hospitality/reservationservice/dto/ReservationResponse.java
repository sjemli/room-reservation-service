package com.marvel.hospitality.reservationservice.dto;

import com.marvel.hospitality.reservationservice.model.ReservationStatus;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Response after creating a room reservation")
public record ReservationResponse(

        @Schema(description = "Unique 8-character uppercase alphanumeric reservation ID", example = "ABC12345")
        String reservationId,

        @Schema(description = "Current status of the reservation",
                allowableValues = {"PENDING_PAYMENT", "CONFIRMED", "CANCELLED"},
                example = "CONFIRMED")
        ReservationStatus status
) {}