package com.marvel.hospitality.reservationservice.dto;


import com.marvel.hospitality.reservationservice.model.PaymentConfirmationStatus;

public record PaymentStatusResponse(String lastUpdateDate, PaymentConfirmationStatus status) {}
