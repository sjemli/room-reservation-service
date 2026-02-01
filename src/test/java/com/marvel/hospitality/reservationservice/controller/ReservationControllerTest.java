package com.marvel.hospitality.reservationservice.controller;

import com.marvel.hospitality.reservationservice.dto.ReservationResponse;
import com.marvel.hospitality.reservationservice.exception.ReservationConflictException;
import com.marvel.hospitality.reservationservice.model.ReservationStatus;
import com.marvel.hospitality.reservationservice.service.ReservationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ReservationController.class)
class ReservationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReservationService service;

    @Test
    void should_returnCreated_when_reservationIsSuccessful() throws Exception {
        when(service.createReservation(any()))
                .thenReturn(new ReservationResponse("ID123", ReservationStatus.CONFIRMED));

        mockMvc.perform(post("/reservations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                {
                    "customerName":"Seif",
                    "roomNumber":"101",
                    "startDate":"2100-02-01",
                    "endDate":"2100-02-05",
                    "segment":"MEDIUM",
                    "paymentMode":"CASH"
                }"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reservationId").value("ID123"));
    }

    @Test
    void should_returnBadRequest_when_dateIsInvalid() throws Exception {
        mockMvc.perform(post("/reservations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                {
                    "customerName":"Seif",
                    "roomNumber":"101",
                    "startDate":"2025-02-01",
                    "endDate":"2026-02-05",
                    "segment":"MEDIUM",
                    "paymentMode":"CASH"
                }"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Bad Request"))
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void should_returnInternalServerError_when_serviceThrowsUnexpectedException() throws Exception {
        when(service.createReservation(any()))
                .thenThrow(new RuntimeException("UnexpectedException"));

        mockMvc.perform(post("/reservations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                {
                    "customerName":"Seif",
                    "roomNumber":"101",
                    "startDate":"2100-02-01",
                    "endDate":"2100-02-05",
                    "segment":"MEDIUM",
                    "paymentMode":"CASH"
                }"""))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.title").value("Internal Server Error"))
                .andExpect(jsonPath("$.status").value(500));
    }

    @Test
    void shouldReturn409Conflict_whenAttemptingToBookOverlappingRoom() throws Exception {

        when(service.createReservation(any()))
                .thenThrow(new ReservationConflictException("already booked"));
        String payload = """
            {
                "customerName": "First Guest",
                "roomNumber": "101",
                "startDate": "2026-03-01",
                "endDate": "2026-03-05",
                "segment": "MEDIUM",
                "paymentMode": "CASH"
            }
            """;

        mockMvc.perform(post("/reservations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(containsString("already booked")));
    }

}