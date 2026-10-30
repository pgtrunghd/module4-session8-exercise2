package com.example.appointmentservice.client;

import com.example.appointmentservice.exception.ResourceNotFoundException;
import com.example.appointmentservice.exception.ServiceUnavailableException;
import com.example.appointmentservice.dto.ApiResponseError;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Component
public class DoctorClient {

    private static final String DOCTOR_URL = "http://doctor-service/api/v1/doctors/{id}";

    private final RestTemplate restTemplate;

    public DoctorClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @CircuitBreaker(name = "doctorServiceCB", fallbackMethod = "getDoctorFallback")
    public ApiResponseError ensureDoctorExists(Long doctorId) {
        try {
            restTemplate.getForObject(DOCTOR_URL, Object.class, doctorId);
            return null;
        } catch (HttpClientErrorException.NotFound e) {
            throw new ResourceNotFoundException("Doctor with id " + doctorId + " not found");
        } catch (RestClientException e) {
            throw new ServiceUnavailableException("Cannot reach doctor-service", e);
        }
    }

    private ApiResponseError getDoctorFallback(Long doctorId, Exception error) {
        if (error instanceof ResourceNotFoundException notFound) {
            throw notFound;
        }
        return new ApiResponseError(503,
                "Hiện tại không thể kiểm tra thông tin bác sĩ, vui lòng thử lại sau vài giây");
    }
}
