package com.marvel.hospitality.reservationservice.service;

import com.marvel.hospitality.reservationservice.client.CreditCardClient;
import com.marvel.hospitality.reservationservice.dto.*;
import com.marvel.hospitality.reservationservice.entity.Reservation;
import com.marvel.hospitality.reservationservice.model.PaymentConfirmationStatus;
import com.marvel.hospitality.reservationservice.model.PaymentMode;
import com.marvel.hospitality.reservationservice.model.ReservationStatus;
import com.marvel.hospitality.reservationservice.exception.*;
import com.marvel.hospitality.reservationservice.repository.ReservationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpClientErrorException;


import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static com.marvel.hospitality.reservationservice.model.PaymentMode.CASH;
import static com.marvel.hospitality.reservationservice.model.ReservationStatus.CONFIRMED;
import static com.marvel.hospitality.reservationservice.model.ReservationStatus.PENDING_PAYMENT;


@Slf4j
@Service
@RequiredArgsConstructor
public class ReservationService  {

    private final ReservationRepository repository;
    private final CreditCardClient creditCardClient;

    @Transactional
    public ReservationResponse createReservation(ReservationRequest request) {
        validateDates(request.startDate(), request.endDate());
        checkForOverlappingReservations(request);

        Reservation reservation = Reservation.builder()
                .customerName(request.customerName())
                .roomNumber(request.roomNumber())
                .startDate(request.startDate())
                .endDate(request.endDate())
                .segment(request.segment())
                .paymentMode(request.paymentMode())
                .paymentReference(request.paymentReference())
                .status(request.paymentMode() == CASH ? CONFIRMED : PENDING_PAYMENT)
                .build();


        if (request.paymentMode() == PaymentMode.CREDIT_CARD) {
            handleCreditCardPayment(reservation, request.paymentReference());
        }

        repository.save(reservation);
        log.info("Created reservation {}", reservation.getId());


        return new ReservationResponse(reservation.getId(), reservation.getStatus());
    }


    private void validateDates(LocalDate start, LocalDate end) {
        if (!end.isAfter(start)) throw new ReservationValidationException("Reservation End date must be after Start date");
        long days = ChronoUnit.DAYS.between(start, end);
        if (days > 30) throw new ReservationValidationException("The Max reservation duration is 30 days");
    }

    private void checkForOverlappingReservations(ReservationRequest request) {
        List<Reservation> overlapping = repository.findOverlappingReservations(
                request.roomNumber(),
                request.startDate(),
                request.endDate()
        );

        if (!overlapping.isEmpty()) {
            throw new ReservationConflictException(
                    "Room " + request.roomNumber() + " is already booked for the requested period"
            );
        }
    }


    private void handleCreditCardPayment(Reservation res, String ref) {
        if (ref == null || ref.isBlank()) {
            throw new ReservationValidationException("paymentReference is required for CreditCard payments");
        }

        PaymentStatusResponse paymentStatusResponse = getStatusResponse(ref);

        if (paymentStatusResponse.status() != PaymentConfirmationStatus.CONFIRMED) {
            throw new PaymentRejectedException("The card payment was REJECTED");
        }
        res.setStatus(ReservationStatus.CONFIRMED);
    }

    private PaymentStatusResponse getStatusResponse(String ref) {
        try {
            return creditCardClient.verifyPayment(ref);
        } catch (HttpClientErrorException clientErrorException) {
            throw new InvalidPaymentReferenceException("Payment Reference was not found or invalid", clientErrorException);
        } catch(Exception e) {
            throw new CreditCardServiceUnavailableException("Credit card service call failed", e);
        }
    }


    @Transactional
    public void confirmBankTransferPayment(String reservationId) {
        Reservation res = repository.findById(reservationId).orElse(null);
        if (res == null) {
            log.warn("Reservation {} not found - skipping", reservationId);
            return;
        }
        if (res.getStatus() == PENDING_PAYMENT &&
                res.getPaymentMode() == PaymentMode.BANK_TRANSFER) {
            res.setStatus(CONFIRMED);
            repository.save(res);
            log.info("Confirmed {}", reservationId);
        } else {
            log.info("Skipped {} (already {})", reservationId, res.getStatus());
        }
    }
}