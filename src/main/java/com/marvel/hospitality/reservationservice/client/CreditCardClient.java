package com.marvel.hospitality.reservationservice.client;

import com.marvel.hospitality.reservationservice.dto.PaymentStatusRequest;
import com.marvel.hospitality.reservationservice.dto.PaymentStatusResponse;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@RequiredArgsConstructor
public class CreditCardClient {

    public static final String PAYMENT_STATUS_PATH = "/payment-status";
    private final RestClient restClient;

    @Value("${credit-card-service.url}")
    @Getter
    private String creditCardUrl;

    @Retry(name = "creditCard")
    @CircuitBreaker(name = "creditCard")
    public PaymentStatusResponse verifyPayment(String reference) {
        return restClient.post()
                .uri(creditCardUrl + PAYMENT_STATUS_PATH)
                .body(new PaymentStatusRequest(reference))
                .retrieve()
                .body(PaymentStatusResponse.class);
    }
}