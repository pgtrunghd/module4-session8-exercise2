package com.example.appointmentservice.service;

import com.example.appointmentservice.dto.AppointmentRequest;
import com.example.appointmentservice.entity.Appointment;
import com.example.appointmentservice.exception.ResourceNotFoundException;
import com.example.appointmentservice.exception.ServiceUnavailableException;
import com.example.appointmentservice.repository.AppointmentRepository;
import com.example.appointmentservice.client.DoctorClient;
import com.example.appointmentservice.dto.ApiResponseError;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Service
public class AppointmentService {

    private static final String PATIENT_URL = "http://patient-service/api/v1/patients/{id}";
    private final AppointmentRepository appointmentRepository;
    private final RestTemplate restTemplate;
    private final DoctorClient doctorClient;

    public AppointmentService(AppointmentRepository appointmentRepository, RestTemplate restTemplate,
                              DoctorClient doctorClient) {
        this.appointmentRepository = appointmentRepository;
        this.restTemplate = restTemplate;
        this.doctorClient = doctorClient;
    }

    public Appointment create(AppointmentRequest request) {
        ensureExists(PATIENT_URL, request.patientId(), "Patient", "patient-service");
        ApiResponseError doctorError = doctorClient.ensureDoctorExists(request.doctorId());
        if (doctorError != null) {
            throw new ServiceUnavailableException(doctorError.message(), null);
        }

        Appointment appointment = new Appointment();
        appointment.setPatientId(request.patientId());
        appointment.setDoctorId(request.doctorId());
        appointment.setAppointmentDate(request.appointmentDate());
        appointment.setReason(request.reason());
        appointment.setStatus("PENDING");
        return appointmentRepository.save(appointment);
    }

    private void ensureExists(String url, Long id, String label, String serviceName) {
        try {
            restTemplate.getForObject(url, Object.class, id);
        } catch (HttpClientErrorException.NotFound e) {
            throw new ResourceNotFoundException(label + " with id " + id + " not found");
        } catch (RestClientException e) {
            throw new ServiceUnavailableException("Cannot reach " + serviceName, e);
        }
    }
}
