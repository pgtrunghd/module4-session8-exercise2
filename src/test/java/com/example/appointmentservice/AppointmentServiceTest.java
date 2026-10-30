package com.example.appointmentservice;

import com.example.appointmentservice.dto.AppointmentRequest;
import com.example.appointmentservice.client.DoctorClient;
import com.example.appointmentservice.entity.Appointment;
import com.example.appointmentservice.exception.ResourceNotFoundException;
import com.example.appointmentservice.repository.AppointmentRepository;
import com.example.appointmentservice.service.AppointmentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class AppointmentServiceTest {

    private AppointmentRepository repository;
    private MockRestServiceServer server;
    private AppointmentService service;

    @BeforeEach
    void setUp() {
        RestTemplate restTemplate = new RestTemplate();
        server = MockRestServiceServer.bindTo(restTemplate).build();
        repository = mock(AppointmentRepository.class);
        service = new AppointmentService(repository, restTemplate, new DoctorClient(restTemplate));
    }

    private AppointmentRequest request() {
        return new AppointmentRequest(1L, 2L, LocalDateTime.of(2026, 11, 1, 9, 0), "Dau dau");
    }

    @Test
    void createSavesPendingAppointmentWhenPatientAndDoctorExist() {
        server.expect(requestTo("http://patient-service/api/v1/patients/1"))
                .andRespond(withSuccess("{\"id\":1}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://doctor-service/api/v1/doctors/2"))
                .andRespond(withSuccess("{\"id\":2}", MediaType.APPLICATION_JSON));
        when(repository.save(any(Appointment.class))).thenAnswer(i -> i.getArgument(0));

        Appointment saved = service.create(request());

        assertEquals("PENDING", saved.getStatus());
        assertEquals(1L, saved.getPatientId());
        assertEquals(2L, saved.getDoctorId());
        server.verify();
    }

    @Test
    void createFailsWhenPatientNotFound() {
        server.expect(requestTo("http://patient-service/api/v1/patients/1"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThrows(ResourceNotFoundException.class, () -> service.create(request()));
        verify(repository, never()).save(any());
    }

    @Test
    void createFailsWhenDoctorNotFound() {
        server.expect(requestTo("http://patient-service/api/v1/patients/1"))
                .andRespond(withSuccess("{\"id\":1}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://doctor-service/api/v1/doctors/2"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThrows(ResourceNotFoundException.class, () -> service.create(request()));
        verify(repository, never()).save(any());
    }
}
