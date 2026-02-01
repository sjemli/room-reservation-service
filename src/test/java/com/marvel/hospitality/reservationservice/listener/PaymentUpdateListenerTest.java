package com.marvel.hospitality.reservationservice.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.marvel.hospitality.reservationservice.dto.PaymentUpdateEvent;
import com.marvel.hospitality.reservationservice.exception.IllegalPaymentUpdateMessageFormatException;
import com.marvel.hospitality.reservationservice.service.ReservationService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentUpdateListenerTest {

    @Mock
    private ReservationService reservationService;
    @Mock
    private Acknowledgment acknowledgment;

    private PaymentUpdateListener listener;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        listener = new PaymentUpdateListener(reservationService);
    }

    @Test
    void should_throwException_when_payloadIsNotAValidJson() {
        String malformedJson = "{ \"invalid\": \"json\" ";
        ConsumerRecord<String, String> consumerRecord = new ConsumerRecord<>("topic", 0, 0, null, malformedJson);

        assertThatThrownBy(() -> listener.onMessage(consumerRecord, acknowledgment))
                .isInstanceOf(IllegalPaymentUpdateMessageFormatException.class)
                .hasMessageContaining("Unable to parse payment update message");

        verifyNoInteractions(reservationService);
        verifyNoInteractions(acknowledgment);
    }

    @Test
    void should_confirmPaymentAndAcknowledge_when_messageIsValid() throws Exception {
        String validId = "CONF1234";
        String payload = createPayload("E2E-REF " + validId);
        ConsumerRecord<String, String> consumerRecord = new ConsumerRecord<>("topic", 0, 0, null, payload);

        listener.onMessage(consumerRecord, acknowledgment);

        verify(reservationService).confirmBankTransferPayment(validId);
        verify(acknowledgment).acknowledge();
    }

    @ParameterizedTest
    @CsvSource(value = {
            "E2E-REF conf1234 | Invalid reservationId (must be exactly 8 uppercase alphanumeric): conf1234",
            "OnlyOnePart      | Invalid transactionDescription format - expected E2E<10chars> <reservationId>",
            "NULL             | Missing transactionDescription",
            "''               | Missing transactionDescription"
    }, delimiter = '|')
    void should_throwException_when_payloadIsInvalid(String description, String expectedErrorMessage) throws Exception {

        String finalDescription = "NULL".equals(description) ? null : description;
        String payload = createPayload(finalDescription);
        ConsumerRecord<String, String> consumerRecord = new ConsumerRecord<>("topic", 0, 0, null, payload);

        assertThatThrownBy(() -> listener.onMessage(consumerRecord, acknowledgment))
                .isInstanceOf(IllegalPaymentUpdateMessageFormatException.class)
                .hasMessageContaining(expectedErrorMessage);

        verifyNoInteractions(reservationService);
        verifyNoInteractions(acknowledgment);
    }

    private String createPayload(String desc) throws Exception {
        PaymentUpdateEvent event = new PaymentUpdateEvent("TXN123", "ACC1", BigDecimal.TEN, desc);
        return objectMapper.writeValueAsString(event);
    }
}

