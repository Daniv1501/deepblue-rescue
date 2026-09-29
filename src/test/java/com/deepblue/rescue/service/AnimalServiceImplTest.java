package com.deepblue.rescue.service;

import com.deepblue.rescue.domain.Animal;
import com.deepblue.rescue.domain.AnimalSex;
import com.deepblue.rescue.domain.RescueCase;
import com.deepblue.rescue.domain.RescueCenter;
import com.deepblue.rescue.domain.RescueStatus;
import com.deepblue.rescue.dto.response.AnimalResponse;
import com.deepblue.rescue.exception.ResourceNotFoundException;
import com.deepblue.rescue.mapper.AnimalMapper;
import com.deepblue.rescue.repository.AnimalRepository;
import com.deepblue.rescue.service.impl.AnimalServiceImpl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnimalServiceImplTest {

    @Mock
    private AnimalRepository repository;

    @Mock
    private AnimalMapper mapper;

    @InjectMocks
    private AnimalServiceImpl service;

    private RescueCase buildRescueCase(RescueStatus status) {
        RescueCenter center = new RescueCenter("DB-CAR", "DeepBlue Caribbean", "Santa Marta");
        return new RescueCase("RES-001", LocalDate.of(2026, 8, 20), "Bahia Concha", status, center);
    }

    @Test
    void shouldFindAnimalByCode() {
        RescueCase rescueCase = buildRescueCase(RescueStatus.IN_REHABILITATION);
        Animal animal = new Animal("AN-001", "Green Sea Turtle", "Chelonia mydas", AnimalSex.FEMALE, rescueCase);
        AnimalResponse response = new AnimalResponse(
                1L, "AN-001", "Green Sea Turtle", "Chelonia mydas",
                AnimalSex.FEMALE, "RES-001", RescueStatus.IN_REHABILITATION);

        when(repository.findByAnimalCode("AN-001")).thenReturn(Optional.of(animal));
        when(mapper.toResponse(animal)).thenReturn(response);

        AnimalResponse result = service.findByCode("AN-001");

        assertThat(result).isEqualTo(response);
    }

    @Test
    void shouldThrowResourceNotFoundExceptionWhenAnimalDoesNotExist() {
        when(repository.findByAnimalCode("AN-999")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findByCode("AN-999"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void shouldFindAnimalsInRehabilitation() {
        RescueCase rescueCase = buildRescueCase(RescueStatus.IN_REHABILITATION);
        Animal animal = new Animal("AN-001", "Green Sea Turtle", "Chelonia mydas", AnimalSex.FEMALE, rescueCase);
        AnimalResponse response = new AnimalResponse(
                1L, "AN-001", "Green Sea Turtle", "Chelonia mydas",
                AnimalSex.FEMALE, "RES-001", RescueStatus.IN_REHABILITATION);

        when(repository.findByRescueCase_Status(RescueStatus.IN_REHABILITATION))
                .thenReturn(List.of(animal));
        when(mapper.toResponse(animal)).thenReturn(response);

        List<AnimalResponse> result = service.findAnimalsInRehabilitation();

        assertThat(result).containsExactly(response);
    }

    // ------------------------------------------------------------------
    // canReceiveTreatment — true para UNDER_EVALUATION e IN_REHABILITATION,
    // false en cualquier otro estado.
    // ------------------------------------------------------------------

    @Test
    void canReceiveTreatmentShouldReturnTrueWhenInRehabilitation() {
        RescueCase rescueCase = buildRescueCase(RescueStatus.IN_REHABILITATION);
        Animal animal = new Animal("AN-001", "Green Sea Turtle", "Chelonia mydas", AnimalSex.FEMALE, rescueCase);

        when(repository.findByAnimalCode("AN-001")).thenReturn(Optional.of(animal));

        assertThat(service.canReceiveTreatment("AN-001")).isTrue();
    }

    @Test
    void canReceiveTreatmentShouldReturnTrueWhenUnderEvaluation() {
        RescueCase rescueCase = buildRescueCase(RescueStatus.UNDER_EVALUATION);
        Animal animal = new Animal("AN-002", "Manatee", "Trichechus manatus", AnimalSex.MALE, rescueCase);

        when(repository.findByAnimalCode("AN-002")).thenReturn(Optional.of(animal));

        assertThat(service.canReceiveTreatment("AN-002")).isTrue();
    }

    @Test
    void canReceiveTreatmentShouldReturnFalseWhenReleased() {
        RescueCase rescueCase = buildRescueCase(RescueStatus.RELEASED);
        Animal animal = new Animal("AN-003", "Dolphin", "Tursiops truncatus", AnimalSex.FEMALE, rescueCase);

        when(repository.findByAnimalCode("AN-003")).thenReturn(Optional.of(animal));

        assertThat(service.canReceiveTreatment("AN-003")).isFalse();
    }

    @Test
    void canReceiveTreatmentShouldReturnFalseWhenAdmitted() {
        RescueCase rescueCase = buildRescueCase(RescueStatus.ADMITTED);
        Animal animal = new Animal("AN-004", "Pelican", "Pelecanus occidentalis", AnimalSex.UNKNOWN, rescueCase);

        when(repository.findByAnimalCode("AN-004")).thenReturn(Optional.of(animal));

        assertThat(service.canReceiveTreatment("AN-004")).isFalse();
    }
}
