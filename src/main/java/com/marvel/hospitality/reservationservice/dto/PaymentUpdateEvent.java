package com.marvel.hospitality.reservationservice.dto;


import java.math.BigDecimal;


public record PaymentUpdateEvent(
        String paymentId,
        String debtorAccountnumber,
        BigDecimal amountReceived,
        String transactionDescription
) {}
