package com.deepblue.rescue.service;

import com.deepblue.rescue.domain.Animal;
import com.deepblue.rescue.domain.AnimalSex;
import com.deepblue.rescue.domain.RescueCase;
import com.deepblue.rescue.domain.RescueCenter;
import com.deepblue.rescue.domain.RescueStatus;
import com.deepblue.rescue.domain.Specialist;
import com.deepblue.rescue.domain.Treatment;
import com.deepblue.rescue.domain.TreatmentType;
import com.deepblue.rescue.dto.request.CreateTreatmentRequest;
import com.deepblue.rescue.dto.response.TreatmentResponse;
import com.deepblue.rescue.exception.BusinessRuleException;
import com.deepblue.rescue.exception.ResourceNotFoundException;
import com.deepblue.rescue.mapper.TreatmentMapper;
import com.deepblue.rescue.repository.AnimalRepository;
import com.deepblue.rescue.repository.SpecialistRepository;
import com.deepblue.rescue.repository.TreatmentRepository;
import com.deepblue.rescue.service.impl.TreatmentServiceImpl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit test puro: AnimalRepository, SpecialistRepository, TreatmentRepository
 * y TreatmentMapper se reemplazan con Mockito. No se levanta Spring
 * ApplicationContext, ni PostgreSQL, ni Flyway.
 */
@ExtendWith(MockitoExtension.class)
class TreatmentServiceImplTest {

    @Mock
    private AnimalRepository animalRepository;

    @Mock
    private SpecialistRepository specialistRepository;

    @Mock
    private TreatmentRepository treatmentRepository;

    @Mock
    private TreatmentMapper mapper;

    @InjectMocks
    private TreatmentServiceImpl service;

    private RescueCase buildRescueCase(RescueStatus status, LocalDate rescueDate) {
        RescueCenter center = new RescueCenter("DB-CAR", "DeepBlue Caribbean", "Santa Marta");
        return new RescueCase("RES-2026-100", rescueDate, "Bahia Concha", status, center);
    }

    private Animal buildAnimal(RescueCase rescueCase) {
        return new Animal("AN-001", "Green Sea Turtle", "Chelonia mydas", AnimalSex.FEMALE, rescueCase);
    }

    private Specialist buildSpecialist(boolean active) {
        return new Specialist("SPEC-001", "Elena", "Vargas", "elena@deepblue.org", active);
    }

    // ------------------------------------------------------------------
    // TEST 5 — Tratamiento válido -> save()
    // ------------------------------------------------------------------

    @Test
    void shouldRegisterTreatmentWhenAllRulesPass() {
        RescueCase rescueCase = buildRescueCase(RescueStatus.IN_REHABILITATION, LocalDate.of(2026, 8, 20));
        Animal animal = buildAnimal(rescueCase);
        Specialist specialist = buildSpecialist(true);

        CreateTreatmentRequest request = new CreateTreatmentRequest(
                "AN-001", "SPEC-001",
                LocalDateTime.of(2026, 8, 21, 9, 0),
                TreatmentType.WOUND_CARE,
                "Cleaning of left front flipper injury.");

        TreatmentResponse response = new TreatmentResponse(
                1L, "AN-001", "SPEC-001",
                LocalDateTime.of(2026, 8, 21, 9, 0),
                TreatmentType.WOUND_CARE,
                "Cleaning of left front flipper injury.");

        when(animalRepository.findByAnimalCode("AN-001")).thenReturn(Optional.of(animal));
        when(specialistRepository.findByProfessionalCode("SPEC-001")).thenReturn(Optional.of(specialist));
        when(treatmentRepository.save(any(Treatment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(mapper.toResponse(any(Treatment.class))).thenReturn(response);

        TreatmentResponse result = service.register(request);

        assertThat(result).isEqualTo(response);
        verify(treatmentRepository).save(any(Treatment.class));
    }

    // ------------------------------------------------------------------
    // Animal inexistente -> ResourceNotFoundException
    // ------------------------------------------------------------------

    @Test
    void shouldThrowResourceNotFoundExceptionWhenAnimalDoesNotExist() {
        CreateTreatmentRequest request = new CreateTreatmentRequest(
                "AN-999", "SPEC-001", LocalDateTime.now(), TreatmentType.OBSERVATION, "desc");

        when(animalRepository.findByAnimalCode("AN-999")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.register(request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("AN-999");

        verify(treatmentRepository, never()).save(any());
    }

    // ------------------------------------------------------------------
    // Especialista inexistente -> ResourceNotFoundException
    // ------------------------------------------------------------------

    @Test
    void shouldThrowResourceNotFoundExceptionWhenSpecialistDoesNotExist() {
        RescueCase rescueCase = buildRescueCase(RescueStatus.IN_REHABILITATION, LocalDate.of(2026, 8, 20));
        Animal animal = buildAnimal(rescueCase);

        CreateTreatmentRequest request = new CreateTreatmentRequest(
                "AN-001", "SPEC-999", LocalDateTime.now(), TreatmentType.OBSERVATION, "desc");

        when(animalRepository.findByAnimalCode("AN-001")).thenReturn(Optional.of(animal));
        when(specialistRepository.findByProfessionalCode("SPEC-999")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.register(request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("SPEC-999");

        verify(treatmentRepository, never()).save(any());
    }

    // ------------------------------------------------------------------
    // TEST 6 — Especialista inactivo -> BusinessRuleException, nunca save()
    // ------------------------------------------------------------------

    @Test
    void shouldThrowBusinessRuleExceptionWhenSpecialistIsInactive() {
        RescueCase rescueCase = buildRescueCase(RescueStatus.IN_REHABILITATION, LocalDate.of(2026, 8, 20));
        Animal animal = buildAnimal(rescueCase);
        Specialist inactiveSpecialist = buildSpecialist(false);

        CreateTreatmentRequest request = new CreateTreatmentRequest(
                "AN-001", "SPEC-001",
                LocalDateTime.of(2026, 8, 21, 9, 0),
                TreatmentType.WOUND_CARE, "desc");

        when(animalRepository.findByAnimalCode("AN-001")).thenReturn(Optional.of(animal));
        when(specialistRepository.findByProfessionalCode("SPEC-001")).thenReturn(Optional.of(inactiveSpecialist));

        assertThatThrownBy(() -> service.register(request))
                .isInstanceOf(BusinessRuleException.class);

        verify(treatmentRepository, never()).save(any());
    }

    // ------------------------------------------------------------------
    // TEST 7 — Caso RELEASED -> BusinessRuleException
    // ------------------------------------------------------------------

    @Test
    void shouldThrowBusinessRuleExceptionWhenCaseIsReleased() {
        RescueCase rescueCase = buildRescueCase(RescueStatus.RELEASED, LocalDate.of(2026, 8, 20));
        Animal animal = buildAnimal(rescueCase);
        Specialist specialist = buildSpecialist(true);

        CreateTreatmentRequest request = new CreateTreatmentRequest(
                "AN-001", "SPEC-001",
                LocalDateTime.of(2026, 9, 1, 9, 0),
                TreatmentType.OBSERVATION, "desc");

        when(animalRepository.findByAnimalCode("AN-001")).thenReturn(Optional.of(animal));
        when(specialistRepository.findByProfessionalCode("SPEC-001")).thenReturn(Optional.of(specialist));

        assertThatThrownBy(() -> service.register(request))
                .isInstanceOf(BusinessRuleException.class);

        verify(treatmentRepository, never()).save(any());
    }

    // ------------------------------------------------------------------
    // Caso CLOSED -> BusinessRuleException (misma regla que RELEASED)
    // ------------------------------------------------------------------

    @Test
    void shouldThrowBusinessRuleExceptionWhenCaseIsClosed() {
        RescueCase rescueCase = buildRescueCase(RescueStatus.CLOSED, LocalDate.of(2026, 8, 20));
        Animal animal = buildAnimal(rescueCase);
        Specialist specialist = buildSpecialist(true);

        CreateTreatmentRequest request = new CreateTreatmentRequest(
                "AN-001", "SPEC-001",
                LocalDateTime.of(2026, 9, 1, 9, 0),
                TreatmentType.OBSERVATION, "desc");

        when(animalRepository.findByAnimalCode("AN-001")).thenReturn(Optional.of(animal));
        when(specialistRepository.findByProfessionalCode("SPEC-001")).thenReturn(Optional.of(specialist));

        assertThatThrownBy(() -> service.register(request))
                .isInstanceOf(BusinessRuleException.class);

        verify(treatmentRepository, never()).save(any());
    }

    // ------------------------------------------------------------------
    // Fecha del tratamiento anterior a rescueDate -> BusinessRuleException
    // (Reto integrador, Solicitud inválida 1)
    // ------------------------------------------------------------------

    @Test
    void shouldThrowBusinessRuleExceptionWhenTreatmentDateIsBeforeRescueDate() {
        RescueCase rescueCase = buildRescueCase(RescueStatus.IN_REHABILITATION, LocalDate.of(2026, 8, 20));
        Animal animal = buildAnimal(rescueCase);
        Specialist specialist = buildSpecialist(true);

        CreateTreatmentRequest request = new CreateTreatmentRequest(
                "AN-001", "SPEC-001",
                LocalDateTime.of(2026, 8, 15, 9, 0), // anterior a 2026-08-20
                TreatmentType.WOUND_CARE, "desc");

        when(animalRepository.findByAnimalCode("AN-001")).thenReturn(Optional.of(animal));
        when(specialistRepository.findByProfessionalCode("SPEC-001")).thenReturn(Optional.of(specialist));

        assertThatThrownBy(() -> service.register(request))
                .isInstanceOf(BusinessRuleException.class);

        verify(treatmentRepository, never()).save(any());
    }

    // ------------------------------------------------------------------
    // findByAnimalCode
    // ------------------------------------------------------------------

    @Test
    void shouldFindTreatmentsByAnimalCode() {
        RescueCase rescueCase = buildRescueCase(RescueStatus.IN_REHABILITATION, LocalDate.of(2026, 8, 20));
        Animal animal = buildAnimal(rescueCase);
        Specialist specialist = buildSpecialist(true);
        Treatment treatment = new Treatment(
                animal, specialist, LocalDateTime.of(2026, 8, 21, 9, 0),
                TreatmentType.WOUND_CARE, "desc");

        TreatmentResponse response = new TreatmentResponse(
                1L, "AN-001", "SPEC-001",
                LocalDateTime.of(2026, 8, 21, 9, 0), TreatmentType.WOUND_CARE, "desc");

        when(treatmentRepository.findByAnimal_AnimalCodeOrderByPerformedAtAsc("AN-001"))
                .thenReturn(List.of(treatment));
        when(mapper.toResponse(treatment)).thenReturn(response);

        List<TreatmentResponse> result = service.findByAnimalCode("AN-001");

        assertThat(result).containsExactly(response);
    }
}
