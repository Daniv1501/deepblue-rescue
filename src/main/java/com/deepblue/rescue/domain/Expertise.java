package com.deepblue.rescue.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "expertise")
public class Expertise {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, unique = true, length = 100)
	private String name;

	@ManyToMany(mappedBy = "expertiseAreas")
	private Set<Specialist> specialists = new HashSet<>();

	protected Expertise() {
	}

	public Expertise(String name) {
		this.name = name;
	}

	public Long getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public Set<Specialist> getSpecialists() {
		return specialists;
	}

	void assignSpecialist(Specialist specialist) {
		specialists.add(specialist);
	}
}
