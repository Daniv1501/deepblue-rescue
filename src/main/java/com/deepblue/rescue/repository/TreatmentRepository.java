package com.deepblue.rescue.repository;

import com.deepblue.rescue.domain.Treatment;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.List;

public interface TreatmentRepository extends JpaRepository<Treatment, Long> {

	List<Treatment> findByAnimal_IdOrderByPerformedAtAsc(Long animalId);

	// Requerido por TreatmentServiceImpl.findByAnimalCode (Paso 22 / Paso 24):
	// navega Treatment -> Animal -> animalCode en vez de usar el id numérico.
	List<Treatment> findByAnimal_AnimalCodeOrderByPerformedAtAsc(String animalCode);

	@Query("""
			select t
			from Treatment t
			where t.performedAt between :start and :end
			order by t.performedAt asc
			""")
	List<Treatment> findBetween(
			@Param("start") LocalDateTime start,
			@Param("end") LocalDateTime end);

	@Query("""
			select t
			from Treatment t
			join t.animal a
			join a.rescueCase rescueCase
			join rescueCase.rescueCenter center
			where center.code = :centerCode
			""")
	List<Treatment> findByRescueCenterCode(
			@Param("centerCode") String centerCode);

	@Query("""
			select distinct t
			from Treatment t
			join t.specialist specialist
			join specialist.expertiseAreas expertise
			where expertise.name = :expertiseName
			""")
	List<Treatment> findBySpecialistExpertise(
			@Param("expertiseName") String expertiseName);
}
