package com.deepblue.rescue.repository;

import com.deepblue.rescue.domain.RescueCase;
import com.deepblue.rescue.domain.RescueStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface RescueCaseRepository extends JpaRepository<RescueCase, Long> {

	Optional<RescueCase> findByCaseCode(String caseCode);

	List<RescueCase> findByStatusOrderByRescueDateAsc(RescueStatus status);

	List<RescueCase> findByRescueCenter_Code(String rescueCenterCode);

	List<RescueCase> findByRescueDateAfterOrderByRescueDateDesc(LocalDate rescueDate);
}
