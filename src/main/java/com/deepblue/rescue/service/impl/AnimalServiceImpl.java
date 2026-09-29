package com.deepblue.rescue.service.impl;

import com.deepblue.rescue.domain.Animal;
import com.deepblue.rescue.domain.RescueStatus;
import com.deepblue.rescue.dto.response.AnimalResponse;
import com.deepblue.rescue.exception.ResourceNotFoundException;
import com.deepblue.rescue.mapper.AnimalMapper;
import com.deepblue.rescue.repository.AnimalRepository;
import com.deepblue.rescue.service.AnimalService;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

@Service
@Transactional(readOnly = true)
public class AnimalServiceImpl
        implements AnimalService {

    // Estados en los que un animal puede recibir tratamientos (Parte XII, Paso 57).
    private static final Set<RescueStatus> TREATABLE_STATUSES =
            Set.of(RescueStatus.UNDER_EVALUATION, RescueStatus.IN_REHABILITATION);

    private final AnimalRepository repository;

    private final AnimalMapper mapper;

    public AnimalServiceImpl(
            AnimalRepository repository,
            AnimalMapper mapper) {

        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    public AnimalResponse findByCode(
            String animalCode) {

        return repository
                .findByAnimalCode(animalCode)
                .map(mapper::toResponse)
                .orElseThrow(
                    () -> new ResourceNotFoundException(
                        "Animal not found: " + animalCode
                    )
                );
    }

    @Override
    public List<AnimalResponse> findAnimalsInRehabilitation() {

        return repository
                .findByRescueCase_Status(RescueStatus.IN_REHABILITATION)
                .stream()
                .map(mapper::toResponse)
                .toList();
    }

    @Override
    public boolean canReceiveTreatment(
            String animalCode) {

        Animal animal = repository
                .findByAnimalCode(animalCode)
                .orElseThrow(
                    () -> new ResourceNotFoundException(
                        "Animal not found: " + animalCode
                    )
                );

        RescueStatus status = animal.getRescueCase().getStatus();

        return TREATABLE_STATUSES.contains(status);
    }
}
