package com.marvel.hospitality.reservationservice.controller;

import com.marvel.hospitality.reservationservice.dto.ReservationRequest;
import com.marvel.hospitality.reservationservice.dto.ReservationResponse;
import com.marvel.hospitality.reservationservice.service.ReservationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/reservations")
@RequiredArgsConstructor
public class ReservationController {

    private final ReservationService service;

    @Operation(
            summary = "Submits a room reservation",
            description = """
            Submits a room reservation and gets a confirmation based on the payment mode:
            - CASH: Room is confirmed immediately
            - CREDIT_CARD: Calls external credit-card-payment-service to verify payment
            - BANK_TRANSFER: Room is booked with PENDING_PAYMENT status (confirmation via Kafka later)
            
            Validations:
            - Reservation cannot exceed 30 days
            - Start date must be in the future or today
            - End date must be after start date
            - All required fields must be provided
            """
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "201",
                    description = "Reservation successfully created and confirmed (or pending for bank transfer)",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ReservationResponse.class),
                            examples = @ExampleObject(value = """
                    {
                      "reservationId": "ABC12345",
                      "status": "CONFIRMED"
                    }
                    """
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid request data (validation errors, rejected payment, etc.)",
                    content = @Content(mediaType = "application/problem+json")
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "Room already booked during that period",
                    content = @Content(mediaType = "application/problem+json")
            ),
            @ApiResponse(
                    responseCode = "503",
                    description = "Credit card service unavailable (circuit open or external 503)",
                    content = @Content(mediaType = "application/problem+json")
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "Internal Error",
                    content = @Content(mediaType = "application/problem+json")
            )
    })
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ReservationResponse createReservation(
            @Valid
            @RequestBody
            @Parameter(
                    description = "Reservation request details including customer info, dates, segment and payment mode",
                    required = true
            )
            ReservationRequest request
    ) {
        return service.createReservation(request);
    }
}
