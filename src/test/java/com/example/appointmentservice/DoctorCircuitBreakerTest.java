package com.example.appointmentservice;

import com.example.appointmentservice.client.DoctorClient;
import com.example.appointmentservice.dto.ApiResponseError;
import com.example.appointmentservice.exception.ResourceNotFoundException;
import com.example.appointmentservice.repository.AppointmentRepository;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {
        "eureka.client.enabled=false",
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration"
})
class DoctorCircuitBreakerTest {

    private static final String DOCTOR_URL = "http://doctor-service/api/v1/doctors/{id}";

    @Autowired
    private DoctorClient doctorClient;

    @Autowired
    private CircuitBreakerRegistry registry;

    @MockitoBean
    private AppointmentRepository repository;

    @MockitoBean
    private RestTemplate restTemplate;

    @BeforeEach
    void resetCircuitBreaker() {
        registry.circuitBreaker("doctorServiceCB").reset();
    }

    @Test
    void opensAfterFiveFailuresAndBlocksTheSixthCall() {
        when(restTemplate.getForObject(DOCTOR_URL, Object.class, 2L))
                .thenThrow(new ResourceAccessException("Doctor-Service is down"));

        for (int i = 0; i < 5; i++) {
            assertEquals(new ApiResponseError(503,
                    "Hiện tại không thể kiểm tra thông tin bác sĩ, vui lòng thử lại sau vài giây"),
                    doctorClient.ensureDoctorExists(2L));
        }

        assertEquals(CircuitBreaker.State.OPEN, registry.circuitBreaker("doctorServiceCB").getState());
        assertEquals(503, doctorClient.ensureDoctorExists(2L).status());
        verify(restTemplate, times(5)).getForObject(DOCTOR_URL, Object.class, 2L);
    }

    @Test
    void missingDoctorDoesNotOpenTheCircuit() {
        when(restTemplate.getForObject(DOCTOR_URL, Object.class, 2L))
                .thenThrow(HttpClientErrorException.create(HttpStatus.NOT_FOUND, "Not Found", null, null, null));

        for (int i = 0; i < 5; i++) {
            assertThrows(ResourceNotFoundException.class, () -> doctorClient.ensureDoctorExists(2L));
        }

        assertEquals(CircuitBreaker.State.CLOSED, registry.circuitBreaker("doctorServiceCB").getState());
        verify(restTemplate, times(5)).getForObject(DOCTOR_URL, Object.class, 2L);
    }
}
