package com.marvel.hospitality.reservationservice.e2e;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.marvel.hospitality.reservationservice.dto.PaymentUpdateEvent;
import com.marvel.hospitality.reservationservice.dto.ReservationRequest;
import com.marvel.hospitality.reservationservice.entity.Reservation;
import com.marvel.hospitality.reservationservice.model.PaymentMode;
import com.marvel.hospitality.reservationservice.model.ReservationStatus;
import com.marvel.hospitality.reservationservice.repository.ReservationRepository;
import com.marvel.hospitality.reservationservice.scheduler.ReservationScheduler;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import lombok.SneakyThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.wiremock.spring.ConfigureWireMock;
import org.wiremock.spring.EnableWireMock;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;


import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.marvel.hospitality.reservationservice.model.PaymentMode.BANK_TRANSFER;
import static com.marvel.hospitality.reservationservice.model.PaymentMode.CASH;
import static com.marvel.hospitality.reservationservice.model.PaymentMode.CREDIT_CARD;
import static com.marvel.hospitality.reservationservice.model.RoomSegment.LARGE;
import static com.marvel.hospitality.reservationservice.model.RoomSegment.MEDIUM;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;


@SpringBootTest(webEnvironment = RANDOM_PORT)
@ActiveProfiles("test")
@AutoConfigureTestRestTemplate
@EnableKafka
@EmbeddedKafka(partitions = 1, topics = {"bank-transfer-payment-update"})
@EnableWireMock(@ConfigureWireMock(name = "credit-card-payment-server", port = 9090, registerSpringBean = true))
@DirtiesContext
class FullFlowE2ETest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private ReservationRepository repository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private ReservationScheduler scheduler;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;


    @Qualifier("credit-card-payment-server")
    @Autowired
    private WireMockServer wireMockServer;

    @BeforeEach
    void setUp() {
        restTemplate.getRestTemplate().setRequestFactory(new SimpleClientHttpRequestFactory());
        wireMockServer.resetAll();
        circuitBreakerRegistry.circuitBreaker("creditCard").reset();
        repository.deleteAll();
    }

    @Test
    void should_confirmImmediately_when_paymentModeIsCash() {
        var request = new ReservationRequest("Seif", "333", LocalDate.of(2100, 1, 1), LocalDate.of(2100, 1, 5),
                MEDIUM, CASH, null);
        var response = restTemplate.postForEntity("/reservations", request, String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        Reservation created = repository.findAll().getFirst();
        assertThat(created.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(created.getPaymentMode()).isEqualTo(PaymentMode.CASH);
        assertThat(created.getPaymentReference()).isNull();
    }

    @Test
    void should_returnBadRequest_when_stayDurationExceedsLimit() {
        var request = new ReservationRequest("JohnOverstay", "101", LocalDate.of(2100, 1, 1), LocalDate.of(2100, 3, 5),
                LARGE, CASH, null);
        var response = restTemplate.postForEntity("/reservations", request, String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("The Max reservation duration is 30 days");

        assertThat(repository.findAll()).noneMatch(r -> r.getCustomerName().equals("JohnOverstay"));
    }

    @Test
    @SneakyThrows
    void should_returnBadRequest_when_startDateIsInThePastAndNameIsBlank() {
        var request = new ReservationRequest("", "101", LocalDate.of(2000, 1, 1), LocalDate.of(2000, 3, 5),
                LARGE, CASH, null);
        var response = restTemplate.postForEntity("/reservations", request, String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("Start date must be today or in the future");
        assertThat(response.getBody()).contains("Customer name must be between 2 and 100 characters");

        assertThat(repository.findAll()).noneMatch(r -> r.getCustomerName().equals("JohnOverstay"));
    }

    @Test
    void should_confirmReservation_when_creditCardPaymentIsSuccessful() {
        wireMockServer.stubFor(post(urlPathMatching("/credit-card-payment-api/.*"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"status\": \"CONFIRMED\"}")));


        var request = new ReservationRequest("John Card", "101", LocalDate.of(2100, 1, 1), LocalDate.of(2100, 1, 5),
                LARGE, CREDIT_CARD, "PAYREF-123456");
        var response = restTemplate.postForEntity("/reservations", request, String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        Reservation created = repository.findAll().stream()
                .filter(r -> r.getCustomerName().equals("John Card"))
                .findFirst().orElseThrow();
        assertThat(created.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(created.getPaymentMode()).isEqualTo(CREDIT_CARD);
        assertThat(created.getPaymentReference()).isEqualTo("PAYREF-123456");
    }

    @Test
    void should_returnBadRequest_when_creditCardPaymentIsRejected() {

        wireMockServer.stubFor(post(urlPathMatching("/credit-card-payment-api/.*"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"status\": \"REJECTED\"}")));

        var request = new ReservationRequest("John Card", "101", LocalDate.of(2100, 1, 1), LocalDate.of(2100, 1, 5),
                LARGE, CREDIT_CARD, "PAYREF-123456");

        var response = restTemplate.postForEntity("/reservations", request, String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("The card payment was REJECTED");

        assertThat(repository.findAll()).noneMatch(r -> r.getCustomerName().equals("Charlie Reject"));
    }


    @Test
    void should_completeFullLifecycle_when_bankTransferIsProcessed() throws Exception {
        var request = new ReservationRequest("John", "101", LocalDate.of(2100, 1, 1), LocalDate.of(2100, 1, 5),
                MEDIUM, BANK_TRANSFER, null);
        var response = restTemplate.postForEntity("/reservations", request, String.class);
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();

        List<Reservation> reservations = repository.findAll();
        Reservation pending = reservations.getFirst();
        String reservationId = pending.getId();
        assertThat(pending.getStatus()).isEqualTo(ReservationStatus.PENDING_PAYMENT);


        PaymentUpdateEvent event = new PaymentUpdateEvent("pay1", "acc1", BigDecimal.TEN, "E2E1234567 " + reservationId);
        String message = objectMapper.writeValueAsString(event);
        kafkaTemplate.send("bank-transfer-payment-update", message);


        await().atMost(10, SECONDS).untilAsserted(() -> {
            Reservation updated = repository.findById(reservationId).orElseThrow();
            assertThat(updated.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);
        });

        pending.setStartDate(LocalDate.now().minusDays(1));
        repository.save(pending);


        scheduler.cancelOverdueBankTransferReservations();

        Reservation cancelled = repository.findById(reservationId).orElseThrow();
        assertThat(cancelled.getStatus()).isEqualTo(ReservationStatus.CANCELLED);
    }

    @Test
    void should_returnServiceUnavailable_when_circuitBreakerOpensDueToConsecutiveFailures() throws InterruptedException {

        wireMockServer.stubFor(post(urlPathMatching("/credit-card-payment-api/.*"))
                .willReturn(aResponse().withStatus(500)));

        var request = new ReservationRequest("John circuit", "101", LocalDate.of(2100, 1, 1), LocalDate.of(2100, 1, 5),
                MEDIUM, CREDIT_CARD, "PAYREF-77777");

        for (int i = 0; i < 4; i++) {
            restTemplate.postForEntity("/reservations", request, String.class);
            Thread.sleep(100);
        }

        var response = restTemplate.postForEntity("/reservations", request, String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).contains("Try Later - credit card service temporarily unavailable");

        assertThat(repository.findAll()).noneMatch(r -> r.getCustomerName().equals("John circuit"));
    }

    @Test
    void should_returnServiceUnavailable_when_externalCreditCardServiceIsDown() {

        wireMockServer.stubFor(post(urlPathMatching("/credit-card-payment-api/.*"))
                .willReturn(aResponse().withStatus(503)));

        var request = new ReservationRequest("John Unavailable", "101", LocalDate.of(2100, 1, 1),
                LocalDate.of(2100, 1, 5), MEDIUM, CREDIT_CARD, "PAYREF-77777");

        var response = restTemplate.postForEntity("/reservations", request, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).contains("Try Later - credit card service temporarily unavailable");

        assertThat(repository.findAll()).noneMatch(r -> r.getCustomerName().equals("John Unavailable"));
    }

    @Test
    void should_returnBadRequest_when_paymentReferenceIsNotFoundByExternalService() {

        wireMockServer.stubFor(post(urlPathMatching("/credit-card-payment-api/.*"))
                .willReturn(aResponse().withStatus(404)));

        var request = new ReservationRequest("John NotFound", "101", LocalDate.of(2100, 1, 1),
                LocalDate.of(2100, 1, 5), MEDIUM, CREDIT_CARD, "PAYREF-Notfound");

        var response = restTemplate.postForEntity("/reservations", request, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("Payment Reference was not found or invalid");

        assertThat(repository.findAll()).noneMatch(r -> r.getCustomerName().equals("John NotFound"));
    }

    @Test
    void shouldThrowConflict_whenTryingToBookAlreadyReservedRoom_sameDates() {
        // First reservation (successful)
        var firstRequest = new ReservationRequest("First Guest", "101", LocalDate.of(2100, 1, 1),
                LocalDate.of(2100, 1, 5), MEDIUM, CASH, null);

        restTemplate.postForEntity("/reservations", firstRequest, String.class);

        // Second overlapping reservation (same room, same dates)
        var secondRequest = new ReservationRequest("First Guest", "101", LocalDate.of(2100, 1, 1),
                LocalDate.of(2100, 1, 5), MEDIUM, CASH, null);
        var response = restTemplate.postForEntity("/reservations", secondRequest, String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).contains("already booked");

        // Only one reservation exists
        assertThat(repository.findAll()).hasSize(1);
        assertThat(repository.findAll().getFirst().getCustomerName()).isEqualTo("First Guest");
    }

    @Test
    void shouldThrowConflict_whenTryingToBookOverlappingDates() {
        // First reservation: 1–5 March
        wireMockServer.stubFor(post(urlPathMatching("/credit-card-payment-api/.*"))
                .willReturn(okJson("""
                    {
                        "lastUpdateDate": "2026-01-29T12:00:00",
                        "status": "CONFIRMED"
                    }
                    """)));
        var firstRequest = new ReservationRequest("First Guest", "101", LocalDate.of(2100, 3, 1),
                LocalDate.of(2100, 3, 5), MEDIUM, CREDIT_CARD, "PAYREF");
        var firstResponse = restTemplate.postForEntity("/reservations", firstRequest, String.class);
        assertThat(firstResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // Second: overlaps (3–7 March)
        var secondRequest = new ReservationRequest("Second Guest", "101", LocalDate.of(2100, 3, 3),
                LocalDate.of(2100, 3, 7), MEDIUM, CASH, null);
        var secondResponse = restTemplate.postForEntity("/reservations", secondRequest, String.class);
        assertThat(secondResponse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(secondResponse.getBody()).contains("already booked");
    }

    @Test
    void shouldAllowNonOverlappingBooking_forSameRoom() {
        // First: 1–5 March
        var firstRequest = new ReservationRequest("First Guest", "101", LocalDate.of(2100, 3, 1),
                LocalDate.of(2100, 3, 5), MEDIUM, CASH, null);
        var firstResponse = restTemplate.postForEntity("/reservations", firstRequest, String.class);
        assertThat(firstResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // Second: 6–10 March (no overlap)
        var secondRequest = new ReservationRequest("Second Guest", "101", LocalDate.of(2100, 3, 6),
                LocalDate.of(2100, 3, 10), MEDIUM, CREDIT_CARD, "REF");
        wireMockServer.stubFor(post(urlPathMatching("/credit-card-payment-api/.*"))
                .willReturn(okJson("""
                    {
                        "lastUpdateDate": "2026-01-29T12:00:00",
                        "status": "CONFIRMED"
                    }
                    """)));

        var secondResponse = restTemplate.postForEntity("/reservations", secondRequest, String.class);
        assertThat(secondResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        assertThat(repository.findAll()).hasSize(2);
    }
}