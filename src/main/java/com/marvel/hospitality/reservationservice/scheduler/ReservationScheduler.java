package com.marvel.hospitality.reservationservice.scheduler;


import com.marvel.hospitality.reservationservice.entity.Reservation;
import com.marvel.hospitality.reservationservice.model.ReservationStatus;
import com.marvel.hospitality.reservationservice.repository.ReservationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;


import java.time.LocalDate;
import java.util.List;


import static com.marvel.hospitality.reservationservice.model.PaymentMode.BANK_TRANSFER;
import static com.marvel.hospitality.reservationservice.model.ReservationStatus.PENDING_PAYMENT;


@Component
@RequiredArgsConstructor
@Slf4j
public class ReservationScheduler {


    private final ReservationRepository reservationRepository;


    @Scheduled(cron = "${cancel.cron:0 0 0 * * ?}")
    public void cancelOverdueBankTransferReservations() {
        try {
            LocalDate threshold = LocalDate.now().plusDays(2);
            log.info("Starting overdue cancellation check (threshold: {})", threshold);


            List<Reservation> overdue = reservationRepository.findByStatusAndPaymentModeAndStartDateLessThanEqual(
                    PENDING_PAYMENT, BANK_TRANSFER, threshold);


            int count = 0;
            for (Reservation res : overdue) {
                try {
                    res.setStatus(ReservationStatus.CANCELLED);
                    reservationRepository.save(res);
                    count++;
                    log.info("Cancelled reservation {}", res.getId());
                } catch (Exception e) {
                    log.error("Failed to cancel reservation {} - continuing", res.getId(), e);
                }
            }
            log.info("Overdue cancellation completed - processed {} reservations", count);
        } catch (Exception e) {
            log.error("Overdue cancellation task failed - will retry next schedule", e);
        }
    }
}
