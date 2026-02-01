package com.marvel.hospitality.reservationservice.listener;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marvel.hospitality.reservationservice.dto.PaymentUpdateEvent;
import com.marvel.hospitality.reservationservice.exception.IllegalPaymentUpdateMessageFormatException;
import com.marvel.hospitality.reservationservice.service.ReservationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentUpdateListener {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ReservationService service;
    private static final Pattern RESERVATION_ID_PATTERN = Pattern.compile("^[A-Z0-9]{8}$");


    @KafkaListener(topics = "${spring.kafka.topic.payment-update}", groupId = "${spring.kafka.consumer.group-id}")
    public void onMessage(ConsumerRecord<String, String> consumerRecord, Acknowledgment ack) {
        String payload = consumerRecord.value();
        try {
            PaymentUpdateEvent paymentUpdateEvent = getPaymentEvent(payload);
            String reservationId = getReservationId(paymentUpdateEvent);
            log.info("Processing valid bank transfer payment update for reservation {}", reservationId);
            service.confirmBankTransferPayment(reservationId);
            ack.acknowledge();
        } catch (Exception e) {
            log.error("Failed processing message - will retry / send to DLQ: {}", payload, e);
            throw e;
        }
    }

    private PaymentUpdateEvent getPaymentEvent(String payload) {
        try {
            return objectMapper.readValue(payload, PaymentUpdateEvent.class);
        } catch (JsonProcessingException jsonProcessingException) {
            throw new IllegalPaymentUpdateMessageFormatException("Unable to parse payment update message",
                    jsonProcessingException);
        }
    }

    private static String getReservationId(PaymentUpdateEvent event) {
        String desc = event.transactionDescription();
        if (desc == null || desc.trim().isEmpty()) {
            throw new IllegalPaymentUpdateMessageFormatException("Missing transactionDescription");
        }
        String[] parts = desc.trim().split(" ");
        if (parts.length < 2) {
            throw new IllegalPaymentUpdateMessageFormatException("Invalid transactionDescription format - expected E2E<10chars> <reservationId>");
        }
        String reservationId = parts[1].trim();
        if (!RESERVATION_ID_PATTERN.matcher(reservationId).matches()) {
            throw new IllegalPaymentUpdateMessageFormatException("Invalid reservationId (must be exactly 8 uppercase alphanumeric): " + reservationId);
        }
        return reservationId;
    }
}
