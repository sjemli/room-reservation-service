package com.marvel.hospitality.reservationservice.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.marvel.hospitality.reservationservice.dto.PaymentUpdateEvent;
import com.marvel.hospitality.reservationservice.entity.Reservation;
import com.marvel.hospitality.reservationservice.model.PaymentMode;
import com.marvel.hospitality.reservationservice.model.ReservationStatus;
import com.marvel.hospitality.reservationservice.repository.ReservationRepository;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.KafkaMessageListenerContainer;
import org.springframework.kafka.listener.MessageListener;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.ContainerTestUtils;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@EnableKafka
@EmbeddedKafka(
partitions = 1,
topics = {PaymentUpdateListenerIntegrationTest.MAIN_TOPIC, PaymentUpdateListenerIntegrationTest.DLT_TOPIC})
@DirtiesContext
class PaymentUpdateListenerIntegrationTest {

    public static final String MAIN_TOPIC = "bank-transfer-payment-update";
    public static final String DLT_TOPIC = "bank-transfer-payment-update-dlt";

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;
    @Autowired
    private ReservationRepository repository;
    @Autowired
    private EmbeddedKafkaBroker embeddedKafka;


    private final ObjectMapper objectMapper = new ObjectMapper();
    private BlockingQueue<ConsumerRecord<String, String>> dlqRecords;
    private KafkaMessageListenerContainer<String, String> container;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
        dlqRecords = new LinkedBlockingQueue<>();

        var consumerProps = KafkaTestUtils.consumerProps(embeddedKafka, "test-group-" + System.currentTimeMillis(), false);
        var cf = new DefaultKafkaConsumerFactory<String, String>(consumerProps);
        var containerProps = new ContainerProperties(DLT_TOPIC);

        container = new KafkaMessageListenerContainer<>(cf, containerProps);
        container.setupMessageListener((MessageListener<String, String>) dlqRecords::add);
        container.start();

        ContainerTestUtils.waitForAssignment(container, embeddedKafka.getPartitionsPerTopic());
    }

    @AfterEach
    void tearDown() {
        if (container != null) container.stop();
    }

    @Test
    void validMessage_updatesReservationToConfirmed() throws Exception {
        String validId = "ABCD1234";
        saveReservation(validId, ReservationStatus.PENDING_PAYMENT);

        sendEvent(new PaymentUpdateEvent("pay-001", "ACC-987", new BigDecimal("499.99"), "E2E1234567 " + validId));

        await().atMost(10, SECONDS).untilAsserted(() ->
                assertThat(repository.findById(validId).get().getStatus()).isEqualTo(ReservationStatus.CONFIRMED));
        assertThat(dlqRecords.poll(2, SECONDS)).isNull();
    }

    @Test
    void invalidReservationIdFormat_sentToDLQ() throws Exception {
        String invalidId = "abcd1234";
        sendEvent(new PaymentUpdateEvent("pay-003", "ACC-999", new BigDecimal("299.50"), "E2E1234567 " + invalidId));

        ConsumerRecord<String, String> consumerRecord = dlqRecords.poll(15, SECONDS);
        assertThat(consumerRecord).isNotNull();
        assertThat(consumerRecord.value()).contains(invalidId);
        assertThat(repository.findById(invalidId)).isEmpty();
    }

    @Test
    void malformedDescription_sentToDLQ() throws Exception {
        sendEvent(new PaymentUpdateEvent("pay-004", "ACC-111", BigDecimal.ONE, "E2E1234567"));

        ConsumerRecord<String, String> consumerRecord = dlqRecords.poll(15, SECONDS);
        assertThat(consumerRecord).isNotNull();
        assertThat(consumerRecord.value()).contains("E2E1234567");
    }

    @Test
    void alreadyConfirmed_isIdempotent() throws Exception {
        String validId = "XYZW9876";
        saveReservation(validId, ReservationStatus.CONFIRMED);

        sendEvent(new PaymentUpdateEvent("pay-002", "ACC-123", BigDecimal.TEN, "E2E1234567 " + validId));

        await().atMost(5, SECONDS).untilAsserted(() ->
                assertThat(repository.findById(validId).get().getStatus()).isEqualTo(ReservationStatus.CONFIRMED));
        assertThat(dlqRecords.poll(2, SECONDS)).isNull();
    }

    @Test
    void reservationNotFound_acknowledgedNoDlq() throws Exception {
        String nonExisting = "ZZZZ9999";
        sendEvent(new PaymentUpdateEvent("pay-005", "ACC-222", new BigDecimal("150"), "E2E1234567 " + nonExisting));

        Thread.sleep(2000);
        assertThat(dlqRecords).isEmpty();
        assertThat(repository.findById(nonExisting)).isEmpty();
    }

    private void saveReservation(String id, ReservationStatus status) {
        repository.save(Reservation.builder()
                .id(id)
                .status(status)
                .paymentMode(PaymentMode.BANK_TRANSFER)
                .build());
    }

    private void sendEvent(PaymentUpdateEvent event) throws Exception {
        kafkaTemplate.send(MAIN_TOPIC, objectMapper.writeValueAsString(event)).get();
    }
}
