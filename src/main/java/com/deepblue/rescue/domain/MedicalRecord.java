package com.deepblue.rescue.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;

@Entity
@Table(name = "medical_records")
public class MedicalRecord {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@OneToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "animal_id", nullable = false, unique = true)
	private Animal animal;

	@Column(name = "initial_weight", precision = 10, scale = 2)
	private BigDecimal initialWeight;

	@Column(name = "initial_condition", length = 200)
	private String initialCondition;

	@Column(columnDefinition = "text")
	private String injuries;

	@Column(columnDefinition = "text")
	private String observations;

	protected MedicalRecord() {
	}

	public MedicalRecord(Animal animal, BigDecimal initialWeight, String initialCondition,
			String injuries, String observations) {
		this.animal = animal;
		this.initialWeight = initialWeight;
		this.initialCondition = initialCondition;
		this.injuries = injuries;
		this.observations = observations;
	}

	public Long getId() {
		return id;
	}

	public Animal getAnimal() {
		return animal;
	}

	void assignAnimal(Animal animal) {
		this.animal = animal;
	}

	public BigDecimal getInitialWeight() {
		return initialWeight;
	}

	public String getInitialCondition() {
		return initialCondition;
	}

	public String getInjuries() {
		return injuries;
	}

	public String getObservations() {
		return observations;
	}
}
