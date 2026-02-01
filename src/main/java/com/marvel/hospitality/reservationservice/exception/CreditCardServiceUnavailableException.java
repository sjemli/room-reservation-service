package com.marvel.hospitality.reservationservice.exception;


public class CreditCardServiceUnavailableException extends RuntimeException {
    public CreditCardServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
