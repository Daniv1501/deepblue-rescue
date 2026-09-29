package com.deepblue.rescue.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;

@Entity
@Table(name = "rescue_cases")
public class RescueCase {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "case_code", nullable = false, unique = true, length = 50)
	private String caseCode;

	@Column(name = "rescue_date", nullable = false)
	private LocalDate rescueDate;

	@Column(name = "rescue_location", nullable = false, length = 200)
	private String rescueLocation;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private RescueStatus status;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "rescue_center_id", nullable = false)
	private RescueCenter rescueCenter;

	@OneToOne(mappedBy = "rescueCase", cascade = CascadeType.ALL,
			orphanRemoval = true, fetch = FetchType.LAZY)
	private Animal animal;

	protected RescueCase() {
	}

	public RescueCase(String caseCode, LocalDate rescueDate, String rescueLocation,
			RescueStatus status, RescueCenter rescueCenter) {
		this.caseCode = caseCode;
		this.rescueDate = rescueDate;
		this.rescueLocation = rescueLocation;
		this.status = status;
		this.rescueCenter = rescueCenter;
	}

	public Long getId() {
		return id;
	}

	public String getCaseCode() {
		return caseCode;
	}

	public LocalDate getRescueDate() {
		return rescueDate;
	}

	public String getRescueLocation() {
		return rescueLocation;
	}

	public RescueStatus getStatus() {
		return status;
	}

	// Agregado para el laboratorio de capa de servicio: el Service necesita
	// mutar el status tras validar la transición (ver RescueCaseServiceImpl).
	public void changeStatus(RescueStatus status) {
		this.status = status;
	}

	public RescueCenter getRescueCenter() {
		return rescueCenter;
	}

	void assignRescueCenter(RescueCenter rescueCenter) {
		this.rescueCenter = rescueCenter;
	}

	public Animal getAnimal() {
		return animal;
	}

	public void assignAnimal(Animal animal) {
		this.animal = animal;
		animal.assignRescueCase(this);
	}
}
