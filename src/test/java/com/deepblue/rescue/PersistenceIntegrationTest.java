package com.deepblue.rescue;

import com.deepblue.rescue.domain.*;
import com.deepblue.rescue.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Pruebas de integración de la CAPA DE PERSISTENCIA (laboratorio anterior)
 * contra PostgreSQL real vía Testcontainers. Se conserva sin cambios de
 * fondo; sigue siendo válida porque el laboratorio de capa de servicio no
 * modificó el esquema ni las relaciones, solo agregó RescueCase.changeStatus().
 */
@Testcontainers
@SpringBootTest
@Transactional
class PersistenceIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:18-alpine")
                    .withDatabaseName("deepblue_test")
                    .withUsername("deepblue")
                    .withPassword("deepblue");

    @Autowired
    private RescueCenterRepository rescueCenterRepository;

    @Autowired
    private RescueCaseRepository rescueCaseRepository;

    @Autowired
    private AnimalRepository animalRepository;

    @Autowired
    private MedicalRecordRepository medicalRecordRepository;

    @Autowired
    private SpecialistRepository specialistRepository;

    @Autowired
    private ExpertiseRepository expertiseRepository;

    @Autowired
    private TreatmentRepository treatmentRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void flywayAppliedAllMigrations() {
        List<Map<String, Object>> history =
                jdbcTemplate.queryForList("SELECT version FROM flyway_schema_history ORDER BY installed_rank");

        List<String> versions = history.stream()
                .map(row -> String.valueOf(row.get("version")))
                .toList();

        assertThat(versions).contains("1", "2", "3");
    }

    @Test
    void inheritedRepositoryMethodsWorkForRescueCenter() {
        RescueCenter center = new RescueCenter("DB-CAR", "DeepBlue Caribbean Center", "Santa Marta");

        RescueCenter saved = rescueCenterRepository.save(center);

        assertThat(saved.getId()).isNotNull();
        assertThat(rescueCenterRepository.findById(saved.getId())).isPresent();
        assertThat(rescueCenterRepository.existsById(saved.getId())).isTrue();
        assertThat(rescueCenterRepository.count()).isEqualTo(1);
    }

    @Test
    void oneToManyRescueCenterToRescueCase() {
        RescueCenter center = rescueCenterRepository.save(
                new RescueCenter("DB-CAR", "DeepBlue Caribbean", "Santa Marta"));

        RescueCase case1 = new RescueCase(
                "RES-001", LocalDate.now(), "Bahia Concha", RescueStatus.IN_REHABILITATION, center);
        RescueCase case2 = new RescueCase(
                "RES-002", LocalDate.now(), "Taganga", RescueStatus.ADMITTED, center);

        center.addCase(case1);
        center.addCase(case2);

        rescueCaseRepository.save(case1);
        rescueCaseRepository.save(case2);

        List<RescueCase> casesForCenter = rescueCaseRepository.findByRescueCenter_Code("DB-CAR");

        assertThat(casesForCenter).hasSize(2);
        assertThat(casesForCenter).allSatisfy(c ->
                assertThat(c.getRescueCenter().getCode()).isEqualTo("DB-CAR"));
    }

    @Test
    void oneToOneRescueCaseToAnimal() {
        RescueCenter center = rescueCenterRepository.save(
                new RescueCenter("DB-CAR", "DeepBlue Caribbean", "Santa Marta"));

        RescueCase rescueCase = rescueCaseRepository.save(new RescueCase(
                "RES-2026-001", LocalDate.of(2026, 8, 18), "Bahia Concha",
                RescueStatus.IN_REHABILITATION, center));

        Animal animal = new Animal(
                "AN-2026-001", "Green Sea Turtle", "Chelonia mydas", AnimalSex.FEMALE, rescueCase);

        rescueCase.assignAnimal(animal);

        animalRepository.save(animal);

        RescueCase reloadedCase = rescueCaseRepository.findByCaseCode("RES-2026-001").orElseThrow();
        Animal reloadedAnimal = animalRepository.findByAnimalCode("AN-2026-001").orElseThrow();

        assertThat(reloadedCase.getAnimal().getAnimalCode()).isEqualTo("AN-2026-001");
        assertThat(reloadedAnimal.getRescueCase().getCaseCode()).isEqualTo("RES-2026-001");
    }

    @Test
    void oneToOneAnimalToMedicalRecordWithCascade() {
        RescueCenter center = rescueCenterRepository.save(
                new RescueCenter("DB-CAR", "DeepBlue Caribbean", "Santa Marta"));
        RescueCase rescueCase = rescueCaseRepository.save(new RescueCase(
                "RES-2026-002", LocalDate.now(), "Bahia Concha", RescueStatus.ADMITTED, center));

        Animal animal = new Animal(
                "AN-2026-002", "Green Sea Turtle", "Chelonia mydas", AnimalSex.FEMALE, rescueCase);

        MedicalRecord record = new MedicalRecord(
                animal, new BigDecimal("28.40"), "STABLE", "Left front flipper injury", null);

        animal.assignMedicalRecord(record);

        Animal saved = animalRepository.save(animal);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getMedicalRecord()).isNotNull();
        assertThat(saved.getMedicalRecord().getId()).isNotNull();
    }

    @Test
    void manyToManySpecialistExpertise() {
        Expertise trauma = expertiseRepository.findByNameIgnoreCase("Trauma").orElseThrow();
        Expertise rehab = expertiseRepository.findByNameIgnoreCase("Rehabilitation").orElseThrow();

        Specialist elena = new Specialist("SPEC-001", "Elena", "Vargas", "elena@deepblue.org", true);
        elena.addExpertise(trauma);
        elena.addExpertise(rehab);

        Specialist saved = specialistRepository.save(elena);

        Specialist reloaded = specialistRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getExpertiseAreas()).hasSize(2);
    }

    @Test
    void queryMethodByStatus() {
        RescueCenter center = rescueCenterRepository.save(
                new RescueCenter("DB-CAR", "DeepBlue Caribbean", "Santa Marta"));

        rescueCaseRepository.save(new RescueCase(
                "RES-001", LocalDate.now(), "Loc 1", RescueStatus.IN_REHABILITATION, center));
        rescueCaseRepository.save(new RescueCase(
                "RES-002", LocalDate.now(), "Loc 2", RescueStatus.READY_FOR_RELEASE, center));
        rescueCaseRepository.save(new RescueCase(
                "RES-003", LocalDate.now(), "Loc 3", RescueStatus.IN_REHABILITATION, center));

        List<RescueCase> inRehab =
                rescueCaseRepository.findByStatusOrderByRescueDateAsc(RescueStatus.IN_REHABILITATION);

        assertThat(inRehab).hasSize(2);
    }

    @Test
    void queryMethodNavigatingToRescueCenterCode() {
        RescueCenter car = rescueCenterRepository.save(new RescueCenter("DB-CAR", "Caribbean", "Santa Marta"));
        RescueCenter pac = rescueCenterRepository.save(new RescueCenter("DB-PAC", "Pacific", "Buenaventura"));

        RescueCase caseCar = rescueCaseRepository.save(
                new RescueCase("RES-CAR-1", LocalDate.now(), "Loc A", RescueStatus.ADMITTED, car));
        RescueCase casePac = rescueCaseRepository.save(
                new RescueCase("RES-PAC-1", LocalDate.now(), "Loc B", RescueStatus.ADMITTED, pac));

        Animal animalCar = new Animal(
                "AN-CAR-1", "Turtle CAR", "Chelonia mydas", AnimalSex.MALE, caseCar);
        Animal animalPac = new Animal(
                "AN-PAC-1", "Turtle PAC", "Chelonia mydas", AnimalSex.FEMALE, casePac);

        animalRepository.save(animalCar);
        animalRepository.save(animalPac);

        List<Animal> animalsInCar = animalRepository.findByRescueCase_RescueCenter_Code("DB-CAR");

        assertThat(animalsInCar).extracting(Animal::getAnimalCode).containsExactly("AN-CAR-1");
    }

    @Test
    void jpqlActiveSpecialistsByExpertise() {
        Expertise trauma = expertiseRepository.findByNameIgnoreCase("Trauma").orElseThrow();
        Expertise rehab = expertiseRepository.findByNameIgnoreCase("Rehabilitation").orElseThrow();
        Expertise mammals = expertiseRepository.findByNameIgnoreCase("Marine Mammals").orElseThrow();
        Expertise birds = expertiseRepository.findByNameIgnoreCase("Marine Birds").orElseThrow();

        Specialist elena = new Specialist("SPEC-001", "Elena", "Vargas", "elena@deepblue.org", true);
        elena.addExpertise(trauma);
        elena.addExpertise(rehab);

        Specialist mateo = new Specialist("SPEC-002", "Mateo", "Rios", "mateo@deepblue.org", true);
        mateo.addExpertise(mammals);
        mateo.addExpertise(rehab);

        Specialist sofia = new Specialist("SPEC-003", "Sofia", "Lopez", "sofia@deepblue.org", true);
        sofia.addExpertise(birds);
        sofia.addExpertise(trauma);

        specialistRepository.save(elena);
        specialistRepository.save(mateo);
        specialistRepository.save(sofia);

        List<Specialist> traumaSpecialists = specialistRepository.findActiveByExpertise("Trauma");

        assertThat(traumaSpecialists).extracting(Specialist::getFirstName)
                .containsExactlyInAnyOrder("Elena", "Sofia");
    }

    @Test
    void treatmentsOrderedChronologically() {
        RescueCenter center = rescueCenterRepository.save(
                new RescueCenter("DB-CAR", "DeepBlue Caribbean", "Santa Marta"));
        RescueCase rescueCase = rescueCaseRepository.save(
                new RescueCase("RES-100", LocalDate.now(), "Loc", RescueStatus.IN_REHABILITATION, center));
        Animal animal = animalRepository.save(
                new Animal("AN-100", "Green Sea Turtle", "Chelonia mydas", AnimalSex.FEMALE, rescueCase));

        Specialist elena = specialistRepository.save(
                new Specialist("SPEC-001", "Elena", "Vargas", "elena@deepblue.org", true));
        Specialist mateo = specialistRepository.save(
                new Specialist("SPEC-002", "Mateo", "Rios", "mateo@deepblue.org", true));

        Treatment t1 = new Treatment(animal, elena,
                LocalDateTime.of(2026, 8, 1, 10, 0), TreatmentType.WOUND_CARE, "Cleaning");
        Treatment t2 = new Treatment(animal, elena,
                LocalDateTime.of(2026, 8, 10, 10, 0), TreatmentType.HYDRATION, "Fluids");
        Treatment t3 = new Treatment(animal, mateo,
                LocalDateTime.of(2026, 8, 20, 10, 0), TreatmentType.OBSERVATION, "Check-up");

        treatmentRepository.save(t1);
        treatmentRepository.save(t2);
        treatmentRepository.save(t3);

        List<Treatment> ordered = treatmentRepository.findByAnimal_IdOrderByPerformedAtAsc(animal.getId());

        assertThat(ordered).extracting(Treatment::getType)
                .containsExactly(TreatmentType.WOUND_CARE, TreatmentType.HYDRATION, TreatmentType.OBSERVATION);
    }

    @Test
    void jpqlTreatmentsBetweenDates() {
        RescueCenter center = rescueCenterRepository.save(
                new RescueCenter("DB-CAR", "DeepBlue Caribbean", "Santa Marta"));
        RescueCase rescueCase = rescueCaseRepository.save(
                new RescueCase("RES-100", LocalDate.now(), "Loc", RescueStatus.IN_REHABILITATION, center));
        Animal animal = animalRepository.save(
                new Animal("AN-100", "Green Sea Turtle", "Chelonia mydas", AnimalSex.FEMALE, rescueCase));
        Specialist elena = specialistRepository.save(
                new Specialist("SPEC-001", "Elena", "Vargas", "elena@deepblue.org", true));

        treatmentRepository.save(new Treatment(animal, elena,
                LocalDateTime.of(2026, 8, 1, 10, 0), TreatmentType.WOUND_CARE, "Cleaning"));
        treatmentRepository.save(new Treatment(animal, elena,
                LocalDateTime.of(2026, 8, 10, 10, 0), TreatmentType.HYDRATION, "Fluids"));
        treatmentRepository.save(new Treatment(animal, elena,
                LocalDateTime.of(2026, 8, 20, 10, 0), TreatmentType.OBSERVATION, "Check-up"));

        List<Treatment> between = treatmentRepository.findBetween(
                LocalDateTime.of(2026, 8, 5, 0, 0),
                LocalDateTime.of(2026, 8, 15, 0, 0));

        assertThat(between).hasSize(1);
        assertThat(between.get(0).getType()).isEqualTo(TreatmentType.HYDRATION);
    }

    @Test
    void uniqueConstraintViolationOnDuplicateAnimalCode() {
        RescueCenter center = rescueCenterRepository.save(
                new RescueCenter("DB-CAR", "DeepBlue Caribbean", "Santa Marta"));
        RescueCase case1 = rescueCaseRepository.save(
                new RescueCase("RES-100", LocalDate.now(), "Loc 1", RescueStatus.ADMITTED, center));
        RescueCase case2 = rescueCaseRepository.save(
                new RescueCase("RES-101", LocalDate.now(), "Loc 2", RescueStatus.ADMITTED, center));

        animalRepository.saveAndFlush(
                new Animal("AN-100", "Green Sea Turtle", "Chelonia mydas", AnimalSex.FEMALE, case1));

        Animal duplicate = new Animal(
                "AN-100", "Another Turtle", "Chelonia mydas", AnimalSex.MALE, case2);

        assertThrows(DataIntegrityViolationException.class,
                () -> animalRepository.saveAndFlush(duplicate));
    }

    @Test
    void integratorChallengeFullScenario() {
        RescueCenter center = rescueCenterRepository.save(
                new RescueCenter("DB-CAR", "DeepBlue Caribbean", "Santa Marta"));

        RescueCase rescueCase = rescueCaseRepository.save(new RescueCase(
                "RES-2026-100", LocalDate.of(2026, 8, 18), "Bahia Concha",
                RescueStatus.IN_REHABILITATION, center));

        Animal animal = new Animal(
                "AN-2026-100", "Green Sea Turtle", "Chelonia mydas", AnimalSex.FEMALE, rescueCase);
        rescueCase.assignAnimal(animal);

        MedicalRecord record = new MedicalRecord(
                animal, new BigDecimal("27.80"), "STABLE",
                "Injury caused by fishing net", "Possible plastic ingestion");
        animal.assignMedicalRecord(record);

        animalRepository.save(animal);

        Specialist elena = new Specialist("SPEC-001", "Elena", "Vargas", "elena@deepblue.org", true);
        elena.addExpertise(expertiseRepository.findByNameIgnoreCase("Marine Reptiles").orElseThrow());
        elena.addExpertise(expertiseRepository.findByNameIgnoreCase("Trauma").orElseThrow());
        elena.addExpertise(expertiseRepository.findByNameIgnoreCase("Rehabilitation").orElseThrow());
        specialistRepository.save(elena);

        Treatment t1 = new Treatment(animal, elena,
                LocalDateTime.of(2026, 8, 19, 9, 0), TreatmentType.WOUND_CARE,
                "Cleaning of left front flipper");
        Treatment t2 = new Treatment(animal, elena,
                LocalDateTime.of(2026, 8, 19, 11, 0), TreatmentType.HYDRATION,
                "Subcutaneous fluid therapy");
        treatmentRepository.save(t1);
        treatmentRepository.save(t2);

        assertThat(rescueCaseRepository.findByCaseCode("RES-2026-100")).isPresent();

        List<RescueCase> inRehab =
                rescueCaseRepository.findByStatusOrderByRescueDateAsc(RescueStatus.IN_REHABILITATION);
        assertThat(inRehab).extracting(RescueCase::getCaseCode).contains("RES-2026-100");

        List<Animal> animalsInCar = animalRepository.findByRescueCase_RescueCenter_Code("DB-CAR");
        assertThat(animalsInCar).extracting(Animal::getAnimalCode).contains("AN-2026-100");

        List<Animal> turtles = animalRepository.findByCommonNameContainingIgnoreCase("turtle");
        assertThat(turtles).extracting(Animal::getAnimalCode).contains("AN-2026-100");

        List<Specialist> traumaSpecialists = specialistRepository.findActiveByExpertise("Trauma");
        assertThat(traumaSpecialists).extracting(Specialist::getProfessionalCode).contains("SPEC-001");

        List<Treatment> treatments = treatmentRepository.findByAnimal_IdOrderByPerformedAtAsc(animal.getId());
        assertThat(treatments).extracting(Treatment::getType)
                .containsExactly(TreatmentType.WOUND_CARE, TreatmentType.HYDRATION);

        List<Treatment> rehabTreatments = treatmentRepository.findBySpecialistExpertise("Rehabilitation");
        assertThat(rehabTreatments).hasSize(2);

        List<Treatment> between = treatmentRepository.findBetween(
                LocalDateTime.of(2026, 8, 19, 0, 0),
                LocalDateTime.of(2026, 8, 19, 23, 59));
        assertThat(between).hasSize(2);
    }

    @Test
    void animalsInRehabilitationTreatedBySpecialistWithTraumaExpertise() {
        RescueCenter center = rescueCenterRepository.save(
                new RescueCenter("DB-CAR", "DeepBlue Caribbean", "Santa Marta"));

        RescueCase rescueCase = rescueCaseRepository.save(new RescueCase(
                "RES-2026-200", LocalDate.now(), "Bahia Concha", RescueStatus.IN_REHABILITATION, center));

        Animal animal = new Animal(
                "AN-2026-200", "Green Sea Turtle", "Chelonia mydas", AnimalSex.FEMALE, rescueCase);
        rescueCase.assignAnimal(animal);
        animalRepository.save(animal);

        Specialist elena = new Specialist("SPEC-010", "Elena", "Vargas", "elena2@deepblue.org", true);
        elena.addExpertise(expertiseRepository.findByNameIgnoreCase("Trauma").orElseThrow());
        specialistRepository.save(elena);

        treatmentRepository.save(new Treatment(
                animal, elena, LocalDateTime.now(), TreatmentType.WOUND_CARE, "Cleaning"));

        List<Animal> inRehab = animalRepository.findByRescueCase_Status(RescueStatus.IN_REHABILITATION);

        boolean treatedByTraumaSpecialist = inRehab.stream()
                .filter(a -> a.getAnimalCode().equals("AN-2026-200"))
                .anyMatch(a -> a.getTreatments().stream()
                        .anyMatch(t -> t.getSpecialist().getExpertiseAreas().stream()
                                .anyMatch(e -> e.getName().equalsIgnoreCase("Trauma"))));

        assertThat(treatedByTraumaSpecialist).isTrue();
    }
}
