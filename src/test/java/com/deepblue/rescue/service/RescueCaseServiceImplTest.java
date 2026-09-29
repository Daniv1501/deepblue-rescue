package com.deepblue.rescue.service;

import com.deepblue.rescue.domain.RescueCase;
import com.deepblue.rescue.domain.RescueCenter;
import com.deepblue.rescue.domain.RescueStatus;
import com.deepblue.rescue.dto.request.ChangeRescueStatusRequest;
import com.deepblue.rescue.dto.response.RescueCaseResponse;
import com.deepblue.rescue.exception.BusinessRuleException;
import com.deepblue.rescue.exception.ResourceNotFoundException;
import com.deepblue.rescue.mapper.RescueCaseMapper;
import com.deepblue.rescue.repository.RescueCaseRepository;
import com.deepblue.rescue.service.impl.RescueCaseServiceImpl;

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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit test puro: el Repository y el Mapper se reemplazan con Mockito.
 * No se levanta Spring ApplicationContext, ni PostgreSQL, ni Flyway.
 */
@ExtendWith(MockitoExtension.class)
class RescueCaseServiceImplTest {

    @Mock
    private RescueCaseRepository repository;

    @Mock
    private RescueCaseMapper mapper;

    @InjectMocks
    private RescueCaseServiceImpl service;

    private RescueCenter buildCenter() {
        return new RescueCenter("DB-CAR", "DeepBlue Caribbean", "Santa Marta");
    }

    // ------------------------------------------------------------------
    // TEST 1 — RescueCase existente -> retorna DTO
    // ------------------------------------------------------------------

    @Test
    void shouldFindRescueCaseByCode() {
        // Arrange
        RescueCase rescueCase = new RescueCase(
                "RES-001", LocalDate.of(2026, 8, 18), "Bahia Concha",
                RescueStatus.IN_REHABILITATION, buildCenter());

        RescueCaseResponse response = new RescueCaseResponse(
                1L, "RES-001", LocalDate.of(2026, 8, 18), "Bahia Concha",
                RescueStatus.IN_REHABILITATION, "DB-CAR", null);

        when(repository.findByCaseCode("RES-001"))
                .thenReturn(Optional.of(rescueCase));
        when(mapper.toResponse(rescueCase))
                .thenReturn(response);

        // Act
        RescueCaseResponse result = service.findByCode("RES-001");

        // Assert
        assertThat(result).isEqualTo(response);

        verify(repository).findByCaseCode("RES-001");
        verify(mapper).toResponse(rescueCase);
    }

    // ------------------------------------------------------------------
    // TEST 2 — RescueCase inexistente -> ResourceNotFoundException
    // ------------------------------------------------------------------

    @Test
    void shouldThrowResourceNotFoundExceptionWhenCaseDoesNotExist() {
        when(repository.findByCaseCode("RES-999"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findByCode("RES-999"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("RES-999");

        verify(mapper, never()).toResponse(any());
    }

    // ------------------------------------------------------------------
    // findByStatus
    // ------------------------------------------------------------------

    @Test
    void shouldFindRescueCasesByStatus() {
        RescueCase rescueCase = new RescueCase(
                "RES-002", LocalDate.of(2026, 8, 10), "Taganga",
                RescueStatus.ADMITTED, buildCenter());
        RescueCaseResponse response = new RescueCaseResponse(
                2L, "RES-002", LocalDate.of(2026, 8, 10), "Taganga",
                RescueStatus.ADMITTED, "DB-CAR", null);

        when(repository.findByStatusOrderByRescueDateAsc(RescueStatus.ADMITTED))
                .thenReturn(List.of(rescueCase));
        when(mapper.toResponse(rescueCase))
                .thenReturn(response);

        List<RescueCaseResponse> result = service.findByStatus(RescueStatus.ADMITTED);

        assertThat(result).containsExactly(response);
    }

    // ------------------------------------------------------------------
    // TEST 3 — Transición de estado válida -> save()
    // ------------------------------------------------------------------

    @Test
    void shouldChangeStatusOnValidTransition() {
        RescueCase rescueCase = new RescueCase(
                "RES-003", LocalDate.of(2026, 8, 1), "Playa Blanca",
                RescueStatus.ADMITTED, buildCenter());

        RescueCaseResponse response = new RescueCaseResponse(
                3L, "RES-003", LocalDate.of(2026, 8, 1), "Playa Blanca",
                RescueStatus.UNDER_EVALUATION, "DB-CAR", null);

        when(repository.findByCaseCode("RES-003"))
                .thenReturn(Optional.of(rescueCase));
        when(repository.save(rescueCase))
                .thenReturn(rescueCase);
        when(mapper.toResponse(rescueCase))
                .thenReturn(response);

        RescueCaseResponse result = service.changeStatus(
                "RES-003",
                new ChangeRescueStatusRequest(RescueStatus.UNDER_EVALUATION));

        assertThat(result).isEqualTo(response);
        assertThat(rescueCase.getStatus()).isEqualTo(RescueStatus.UNDER_EVALUATION);

        verify(repository).save(rescueCase);
    }

    // ------------------------------------------------------------------
    // TEST 4 — Transición inválida -> BusinessRuleException, nunca save()
    // ------------------------------------------------------------------

    @Test
    void shouldThrowBusinessRuleExceptionOnInvalidTransition() {
        RescueCase rescueCase = new RescueCase(
                "RES-004", LocalDate.of(2026, 8, 1), "Playa Blanca",
                RescueStatus.ADMITTED, buildCenter());

        when(repository.findByCaseCode("RES-004"))
                .thenReturn(Optional.of(rescueCase));

        assertThatThrownBy(() -> service.changeStatus(
                "RES-004",
                new ChangeRescueStatusRequest(RescueStatus.READY_FOR_RELEASE)))
                .isInstanceOf(BusinessRuleException.class);

        verify(repository, never()).save(any());
    }
}
