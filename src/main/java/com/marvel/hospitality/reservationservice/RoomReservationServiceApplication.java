package com.marvel.hospitality.reservationservice;


import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;


@SpringBootApplication
@EnableScheduling
public class RoomReservationServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(RoomReservationServiceApplication.class, args);
    }
}
