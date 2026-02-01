package com.marvel.hospitality.reservationservice.exception;

public class InvalidPaymentReferenceException extends RuntimeException{
    public InvalidPaymentReferenceException(String message, Throwable throwable) {
        super(message, throwable);
    }
}