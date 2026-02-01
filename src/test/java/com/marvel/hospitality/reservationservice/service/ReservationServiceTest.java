package com.marvel.hospitality.reservationservice.service;


import com.marvel.hospitality.reservationservice.client.CreditCardClient;
import com.marvel.hospitality.reservationservice.dto.*;
import com.marvel.hospitality.reservationservice.entity.Reservation;
import com.marvel.hospitality.reservationservice.model.*;
import com.marvel.hospitality.reservationservice.exception.*;
import com.marvel.hospitality.reservationservice.repository.ReservationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;


import java.time.LocalDate;
import java.util.List;
import java.util.Optional;


import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;


@ExtendWith(MockitoExtension.class)
class ReservationServiceTest {

    @Mock
    private ReservationRepository repository;
    @Mock
    private CreditCardClient creditCardClient;

    @InjectMocks
    private ReservationService service;


    @Test
    void should_createConfirmedReservation_when_paymentModeIsCash() {
        ReservationRequest req = new ReservationRequest("John", "101", LocalDate.now(), LocalDate.now().plusDays(2),
                RoomSegment.MEDIUM, PaymentMode.CASH, null);

        when(repository.save(any(Reservation.class))).thenAnswer(i -> i.getArgument(0));

        ReservationResponse resp = service.createReservation(req);

        assertThat(resp.status()).isEqualTo(ReservationStatus.CONFIRMED);
        verify(repository).save(any());
        verifyNoInteractions(creditCardClient);
    }

    @Test
    void should_createConfirmedReservation_when_creditCardPaymentIsSuccessful() {
        ReservationRequest req = new ReservationRequest("John", "101", LocalDate.now(), LocalDate.now().plusDays(2),
                RoomSegment.MEDIUM, PaymentMode.CREDIT_CARD, "REF-123");

        when(creditCardClient.verifyPayment("REF-123"))
                .thenReturn(new PaymentStatusResponse("", PaymentConfirmationStatus.CONFIRMED));
        when(repository.save(any(Reservation.class))).thenAnswer(i -> i.getArgument(0));

        ReservationResponse resp = service.createReservation(req);

        assertThat(resp.status()).isEqualTo(ReservationStatus.CONFIRMED);
        verify(creditCardClient).verifyPayment("REF-123");
    }

    @Test
    void should_throwValidationException_when_creditCardReferenceIsBlank() {
        ReservationRequest req = new ReservationRequest("John", "101", LocalDate.now(), LocalDate.now().plusDays(2),
                RoomSegment.MEDIUM, PaymentMode.CREDIT_CARD, "  ");

        assertThatThrownBy(() -> service.createReservation(req))
                .isInstanceOf(ReservationValidationException.class)
                .hasMessage("paymentReference is required for CreditCard payments");
    }

    @Test
    void should_throwValidationException_when_creditCardReferenceIsMissing() {
        ReservationRequest req = new ReservationRequest("John", "101", LocalDate.now(), LocalDate.now().plusDays(2),
                RoomSegment.MEDIUM, PaymentMode.CREDIT_CARD, null);

        assertThatThrownBy(() -> service.createReservation(req))
                .isInstanceOf(ReservationValidationException.class)
                .hasMessage("paymentReference is required for CreditCard payments");
    }

    @Test
    void should_throwPaymentRejectedException_when_creditCardPaymentIsRejected() {
        ReservationRequest req = new ReservationRequest("John", "101", LocalDate.now(), LocalDate.now().plusDays(2),
                RoomSegment.MEDIUM, PaymentMode.CREDIT_CARD, "REF-FAIL");

        when(creditCardClient.verifyPayment("REF-FAIL"))
                .thenReturn(new PaymentStatusResponse("", PaymentConfirmationStatus.REJECTED));

        assertThatThrownBy(() -> service.createReservation(req))
                .isInstanceOf(PaymentRejectedException.class);
    }

    @Test
    void should_throwValidationException_when_endDateIsBeforeStartDate() {
        ReservationRequest req = new ReservationRequest("John", "101", LocalDate.now().plusDays(5), LocalDate.now(),
                RoomSegment.MEDIUM, PaymentMode.CASH, null);

        assertThatThrownBy(() -> service.createReservation(req))
                .isInstanceOf(ReservationValidationException.class)
                .hasMessage("Reservation End date must be after Start date");
    }

    @Test
    void should_throwValidationException_when_durationExceedsThirtyDays() {
        ReservationRequest req = new ReservationRequest("John", "101", LocalDate.now(), LocalDate.now().plusDays(32),
                RoomSegment.MEDIUM, PaymentMode.CASH, null);

        assertThatThrownBy(() -> service.createReservation(req))
                .isInstanceOf(ReservationValidationException.class)
                .hasMessage("The Max reservation duration is 30 days");
    }

    @Test
    void should_doNothing_when_reservationIdNotFound() {
        when(repository.findById("NONE")).thenReturn(Optional.empty());

        service.confirmBankTransferPayment("NONE");

        verify(repository, never()).save(any());
    }

    @Test
    void should_doNothing_when_paymentModeIsNotBankTransfer() {
        Reservation res = Reservation.builder().status(ReservationStatus.PENDING_PAYMENT)
                .paymentMode(PaymentMode.CASH).build();
        when(repository.findById("ID1")).thenReturn(Optional.of(res));

        service.confirmBankTransferPayment("ID1");

        verify(repository, never()).save(any());
    }

    @Test
    void should_confirmReservation_when_bankTransferPaymentIsVerified() {
        Reservation res = Reservation.builder()
                .status(ReservationStatus.PENDING_PAYMENT)
                .paymentMode(PaymentMode.BANK_TRANSFER).build();
        when(repository.findById("ID1")).thenReturn(Optional.of(res));

        service.confirmBankTransferPayment("ID1");

        assertThat(res.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);
        verify(repository).save(res);
    }

    @Test
    void shouldThrowReservationConflictException_whenRoomIsAlreadyBookedForOverlappingPeriod() {
        Reservation existing = Reservation.builder()
                .id("EXISTING")
                .roomNumber("101")
                .startDate(LocalDate.of(2026, 3, 1))
                .endDate(LocalDate.of(2026, 3, 10))
                .status(ReservationStatus.CONFIRMED)
                .build();

        when(repository.findOverlappingReservations("101", LocalDate.of(2026, 3, 5), LocalDate.of(2026, 3, 15)))
                .thenReturn(List.of(existing));

        ReservationRequest request = new ReservationRequest(
                "New Guest", "101", LocalDate.of(2026, 3, 5), LocalDate.of(2026, 3, 15),
                RoomSegment.MEDIUM, PaymentMode.CASH, null);

        assertThatThrownBy(() -> service.createReservation(request))
                .isInstanceOf(ReservationConflictException.class)
                .hasMessageContaining("Room 101 is already booked");

        verify(repository).findOverlappingReservations(anyString(), any(LocalDate.class), any(LocalDate.class));
        verifyNoMoreInteractions(repository);
    }

    @Test
    void shouldCreateReservation_whenNoOverlappingReservationsExist() {
        when(repository.findOverlappingReservations(anyString(), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(List.of());

        ReservationRequest request = new ReservationRequest(
                "New Guest", "202", LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 5),
                RoomSegment.LARGE, PaymentMode.CASH, null);

        ReservationResponse response = service.createReservation(request);

        assertThat(response.status()).isEqualTo(ReservationStatus.CONFIRMED);
        verify(repository).save(any(Reservation.class));
    }
}