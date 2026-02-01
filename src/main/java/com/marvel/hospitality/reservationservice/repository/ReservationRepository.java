package com.marvel.hospitality.reservationservice.repository;

import com.marvel.hospitality.reservationservice.entity.Reservation;
import com.marvel.hospitality.reservationservice.model.PaymentMode;
import com.marvel.hospitality.reservationservice.model.ReservationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface ReservationRepository extends JpaRepository<Reservation, String> {

    List<Reservation> findByStatusAndPaymentModeAndStartDateLessThanEqual(ReservationStatus status,
                                                                          PaymentMode mode,
                                                                          LocalDate date);

    @Query("""
        SELECT r FROM Reservation r
        WHERE r.roomNumber = :roomNumber
        AND r.status IN ('PENDING_PAYMENT', 'CONFIRMED')
        AND r.endDate > :startDate
        AND r.startDate < :endDate
    """)
    List<Reservation> findOverlappingReservations(
            @Param("roomNumber") String roomNumber,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );
}
