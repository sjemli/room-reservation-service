package com.marvel.hospitality.reservationservice.repository;

import com.marvel.hospitality.reservationservice.entity.Reservation;
import com.marvel.hospitality.reservationservice.model.PaymentMode;
import com.marvel.hospitality.reservationservice.model.ReservationStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class ReservationRepositoryTest {

    @Autowired
    private ReservationRepository repository;

    @Test
    @DisplayName("Should find reservations matching status, payment mode, and date (inclusive)")
    void shouldFindReservationsByCriteria() {
        // Given
        LocalDate cutoffDate = LocalDate.of(2024, 1, 1);

        Reservation validPast = Reservation.builder()
                .customerName("Valid Past")
                .status(ReservationStatus.CONFIRMED)
                .paymentMode(PaymentMode.CREDIT_CARD)
                .startDate(LocalDate.of(2023, 12, 31))
                .build();

        Reservation validExact = Reservation.builder()
                .customerName("Valid Exact")
                .status(ReservationStatus.CONFIRMED)
                .paymentMode(PaymentMode.CREDIT_CARD)
                .startDate(cutoffDate)
                .build();

        Reservation invalidStatus = Reservation.builder()
                .customerName("Invalid Status")
                .status(ReservationStatus.CANCELLED)
                .paymentMode(PaymentMode.CREDIT_CARD)
                .startDate(LocalDate.of(2023, 12, 31))
                .build();

        Reservation invalidDate = Reservation.builder()
                .customerName("Future Date")
                .status(ReservationStatus.CONFIRMED)
                .paymentMode(PaymentMode.CREDIT_CARD)
                .startDate(LocalDate.of(2024, 1, 2))
                .build();

        repository.saveAll(List.of(validPast, validExact, invalidStatus, invalidDate));

        // When
        List<Reservation> results = repository
                .findByStatusAndPaymentModeAndStartDateLessThanEqual(
                        ReservationStatus.CONFIRMED,
                        PaymentMode.CREDIT_CARD,
                        cutoffDate
                );

        // Then
        assertThat(results).hasSize(2);
        assertThat(results).extracting(Reservation::getCustomerName)
                .containsExactlyInAnyOrder("Valid Past", "Valid Exact");
    }

    @Test
    void shouldFindOverlappingReservations_whenDatesOverlap() {
        // Given: two overlapping reservations
        Reservation existing = Reservation.builder()
                .id("EXISTING")
                .roomNumber("101")
                .startDate(LocalDate.of(2026, 3, 1))
                .endDate(LocalDate.of(2026, 3, 10))
                .status(ReservationStatus.CONFIRMED)
                .paymentMode(PaymentMode.CASH)
                .build();
        repository.save(existing);

        // When: query for overlapping period (3-7 March)
        List<Reservation> overlaps = repository.findOverlappingReservations(
                "101",
                LocalDate.of(2026, 3, 3),
                LocalDate.of(2026, 3, 7)
        );

        // Then
        assertThat(overlaps).hasSize(1);
        assertThat(overlaps.getFirst().getId()).isEqualTo("EXISTING");
    }

    @Test
    void shouldNotFindOverlappingReservations_whenDatesDoNotOverlap() {
        // Given: reservation ends before new start
        Reservation existing = Reservation.builder()
                .id("EXISTING")
                .roomNumber("101")
                .startDate(LocalDate.of(2026, 3, 1))
                .endDate(LocalDate.of(2026, 3, 5))
                .status(ReservationStatus.CONFIRMED)
                .paymentMode(PaymentMode.CASH)
                .build();
        repository.save(existing);

        // Query: 6-10 March (no overlap)
        List<Reservation> overlaps = repository.findOverlappingReservations(
                "101",
                LocalDate.of(2026, 3, 6),
                LocalDate.of(2026, 3, 10)
        );

        assertThat(overlaps).isEmpty();
    }

    @Test
    void shouldFindOverlappingReservations_whenStatusIsPendingPayment() {
        // Given: pending reservation overlaps
        Reservation pending = Reservation.builder()
                .id("PENDING")
                .roomNumber("202")
                .startDate(LocalDate.of(2026, 4, 1))
                .endDate(LocalDate.of(2026, 4, 10))
                .status(ReservationStatus.PENDING_PAYMENT)
                .paymentMode(PaymentMode.BANK_TRANSFER)
                .build();
        repository.save(pending);

        // overlapping period
        List<Reservation> overlaps = repository.findOverlappingReservations(
                "202",
                LocalDate.of(2026, 4, 5),
                LocalDate.of(2026, 4, 15)
        );

        assertThat(overlaps).hasSize(1);
        assertThat(overlaps.getFirst().getStatus()).isEqualTo(ReservationStatus.PENDING_PAYMENT);
    }

    @Test
    void shouldNotFindOverlappingReservations_whenStatusIsCancelled() {
        // Given: cancelled reservation overlaps
        Reservation cancelled = Reservation.builder()
                .id("CANCELLED")
                .roomNumber("303")
                .startDate(LocalDate.of(2026, 5, 1))
                .endDate(LocalDate.of(2026, 5, 10))
                .status(ReservationStatus.CANCELLED)
                .paymentMode(PaymentMode.CASH)
                .build();
        repository.save(cancelled);

        // Query: overlapping
        List<Reservation> overlaps = repository.findOverlappingReservations(
                "303",
                LocalDate.of(2026, 5, 3),
                LocalDate.of(2026, 5, 7)
        );

        assertThat(overlaps).isEmpty();
    }
}
