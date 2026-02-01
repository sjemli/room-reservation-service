package com.marvel.hospitality.reservationservice.client;

import com.marvel.hospitality.reservationservice.model.PaymentConfirmationStatus;
import com.marvel.hospitality.reservationservice.dto.PaymentStatusRequest;
import com.marvel.hospitality.reservationservice.dto.PaymentStatusResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CreditCardClientTest {

    @Mock
    private RestClient restClient;

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private RestClient.RequestBodyUriSpec requestBodyUriSpec;

    @InjectMocks
    private CreditCardClient creditCardClient;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(creditCardClient, "creditCardUrl", "http://localhost:8080");
    }

    @Test
    void should_returnConfirmedResponse_when_apiCallIsSuccessful() {
        String ref = "REF-123";
        PaymentStatusResponse expectedResponse = new PaymentStatusResponse(null, PaymentConfirmationStatus.CONFIRMED);

        when(restClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(any(String.class))).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.body(any(PaymentStatusRequest.class))).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.retrieve().body(PaymentStatusResponse.class)).thenReturn(expectedResponse);

        PaymentStatusResponse actualResponse = creditCardClient.verifyPayment(ref);

        assertThat(actualResponse).isNotNull();
        assertThat(actualResponse.status()).isEqualTo(PaymentConfirmationStatus.CONFIRMED);
    }


    @Test
    void should_throwException_when_serverReturnsError() {
        String ref = "REF-500";

        when(restClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(any(String.class))).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.body(any(PaymentStatusRequest.class))).thenReturn(requestBodyUriSpec);

        when(requestBodyUriSpec.retrieve()).thenThrow(
                new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR, "Server Error")
        );

        assertThatThrownBy(() -> creditCardClient.verifyPayment(ref))
                .isInstanceOf(HttpServerErrorException.class)
                .hasMessageContaining("500 Server Error");
    }
}