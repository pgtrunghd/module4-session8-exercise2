package com.example.appointmentservice.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public record AppointmentRequest(
        @NotNull(message = "patientId must not be null") Long patientId,
        @NotNull(message = "doctorId must not be null") Long doctorId,
        LocalDateTime appointmentDate,
        String reason) {
}
