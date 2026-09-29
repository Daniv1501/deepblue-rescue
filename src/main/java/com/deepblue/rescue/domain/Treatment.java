package com.deepblue.rescue.domain;

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
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "treatments")
public class Treatment {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "animal_id", nullable = false)
	private Animal animal;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "specialist_id", nullable = false)
	private Specialist specialist;

	@Column(name = "performed_at", nullable = false)
	private LocalDateTime performedAt;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private TreatmentType type;

	@Column(columnDefinition = "text")
	private String description;

	protected Treatment() {
	}

	public Treatment(Animal animal, Specialist specialist, LocalDateTime performedAt,
			TreatmentType type, String description) {
		this.animal = animal;
		this.specialist = specialist;
		this.performedAt = performedAt;
		this.type = type;
		this.description = description;
	}

	public Long getId() {
		return id;
	}

	public Animal getAnimal() {
		return animal;
	}

	public Specialist getSpecialist() {
		return specialist;
	}

	public LocalDateTime getPerformedAt() {
		return performedAt;
	}

	public TreatmentType getType() {
		return type;
	}

	public String getDescription() {
		return description;
	}
}
