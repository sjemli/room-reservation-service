package com.marvel.hospitality.reservationservice.scheduler;


import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.marvel.hospitality.reservationservice.entity.Reservation;
import com.marvel.hospitality.reservationservice.model.ReservationStatus;
import com.marvel.hospitality.reservationservice.repository.ReservationRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.util.List;


import static com.marvel.hospitality.reservationservice.model.PaymentMode.BANK_TRANSFER;
import static com.marvel.hospitality.reservationservice.model.ReservationStatus.PENDING_PAYMENT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;


@ExtendWith(MockitoExtension.class)
class ReservationSchedulerTest {


    @Mock
    private ReservationRepository repository;


    @InjectMocks
    private ReservationScheduler scheduler;


    @Captor
    private ArgumentCaptor<Reservation> reservationCaptor;


    private ListAppender<ILoggingEvent> logAppender;
    private Logger logger;


    @BeforeEach
    void setUp() {
        logAppender = new ListAppender<>();
        logAppender.start();
        logger = (Logger) LoggerFactory.getLogger(ReservationScheduler.class);
        logger.addAppender(logAppender);
    }


    @AfterEach
    void tearDown() {
        logger.detachAppender(logAppender);
        logAppender.stop();
    }


    @Test
    void should_cancelAndLog_when_overdueReservationsExist() {
        Reservation overdue = Reservation.builder().id("RES001").status(PENDING_PAYMENT)
                .paymentMode(BANK_TRANSFER).startDate(LocalDate.now().plusDays(1)).build();
        when(repository.findByStatusAndPaymentModeAndStartDateLessThanEqual(any(), any(), any()))
                .thenReturn(List.of(overdue));

        scheduler.cancelOverdueBankTransferReservations();

        verify(repository).save(reservationCaptor.capture());
        assertThat(reservationCaptor.getValue().getStatus()).isEqualTo(ReservationStatus.CANCELLED);


        List<ILoggingEvent> logs = logAppender.list;
        assertThat(logs).anyMatch(e -> e.getFormattedMessage().contains("Starting overdue cancellation check"));
        assertThat(logs).anyMatch(e -> e.getFormattedMessage().contains("Cancelled reservation RES001"));
    }


    @Test
    void should_logAndNotSave_when_noOverdueReservationsFound() {
        when(repository.findByStatusAndPaymentModeAndStartDateLessThanEqual(any(), any(), any()))
                .thenReturn(List.of());


        scheduler.cancelOverdueBankTransferReservations();


        verify(repository, never()).save(any());


        List<ILoggingEvent> logs = logAppender.list;
        assertThat(logs).anyMatch(e -> e.getFormattedMessage().contains("processed 0 reservations"));
    }


    @Test
    void should_logFailure_when_queryExceptionOccurs() {
        when(repository.findByStatusAndPaymentModeAndStartDateLessThanEqual(any(), any(), any()))
                .thenThrow(new RuntimeException("DB error"));


        scheduler.cancelOverdueBankTransferReservations();


        List<ILoggingEvent> logs = logAppender.list;
        assertThat(logs).anyMatch(e ->
                e.getFormattedMessage().contains("Overdue cancellation task failed"));
    }

    @Test
    void should_logFailure_and_continue_processing_when_ExceptionOccurs_forOneReservation() {
        Reservation badReservation = Reservation.builder().id("RES-BAD").status(PENDING_PAYMENT)
                .paymentMode(BANK_TRANSFER).startDate(LocalDate.now().plusDays(1)).build();
        Reservation goodReservation = Reservation.builder().id("RES-GOOD").status(PENDING_PAYMENT)
                .paymentMode(BANK_TRANSFER).startDate(LocalDate.now().plusDays(1)).build();

        when(repository.findByStatusAndPaymentModeAndStartDateLessThanEqual(any(), any(), any()))
                .thenReturn(List.of(badReservation, goodReservation));

        when(repository.save(argThat(res -> "RES-BAD".equals(res.getId()))))
                .thenThrow(new RuntimeException("DB glitch"));
        scheduler.cancelOverdueBankTransferReservations();


        List<String> logMessages = logAppender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
        assertThat(logMessages).anyMatch(s -> s.contains("Cancelled reservation RES-GOOD"));
        assertThat(logMessages).anyMatch(s -> s.contains("Failed to cancel reservation RES-BAD - continuing"));
    }
}
