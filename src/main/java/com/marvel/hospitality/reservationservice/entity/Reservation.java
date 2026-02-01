package com.marvel.hospitality.reservationservice.entity;


import com.marvel.hospitality.reservationservice.model.PaymentMode;
import com.marvel.hospitality.reservationservice.model.ReservationStatus;
import com.marvel.hospitality.reservationservice.model.RoomSegment;
import jakarta.persistence.*;
import lombok.*;


import java.time.LocalDate;
import java.time.LocalDateTime;


@Entity
@Table(name = "reservations")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Reservation {
    @Id
    private String id;


    private String customerName;
    private String roomNumber;
    private LocalDate startDate;
    private LocalDate endDate;
    @Enumerated(EnumType.STRING)
    private RoomSegment segment;
    @Enumerated(EnumType.STRING)
    private PaymentMode paymentMode;
    private String paymentReference;
    @Enumerated(EnumType.STRING)
    private ReservationStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;


    @PrePersist
    protected void onCreate() {
        createdAt = updatedAt = LocalDateTime.now();
        if (id == null) {
            id = java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
        }
    }


    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
