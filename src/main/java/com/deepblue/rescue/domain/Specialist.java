package com.deepblue.rescue.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Entity
@Table(name = "specialists")
public class Specialist {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "professional_code", nullable = false, unique = true, length = 50)
	private String professionalCode;

	@Column(name = "first_name", nullable = false, length = 100)
	private String firstName;

	@Column(name = "last_name", nullable = false, length = 100)
	private String lastName;

	@Column(nullable = false, unique = true, length = 200)
	private String email;

	@Column(nullable = false)
	private boolean active;

	@ManyToMany
	@JoinTable(name = "specialist_expertise",
			joinColumns = @JoinColumn(name = "specialist_id"),
			inverseJoinColumns = @JoinColumn(name = "expertise_id"))
	private Set<Expertise> expertiseAreas = new HashSet<>();

	@OneToMany(mappedBy = "specialist", cascade = CascadeType.ALL,
			orphanRemoval = true)
	private List<Treatment> treatments = new ArrayList<>();

	protected Specialist() {
	}

	public Specialist(String professionalCode, String firstName, String lastName,
			String email, boolean active) {
		this.professionalCode = professionalCode;
		this.firstName = firstName;
		this.lastName = lastName;
		this.email = email;
		this.active = active;
	}

	public Long getId() {
		return id;
	}

	public String getProfessionalCode() {
		return professionalCode;
	}

	public String getFirstName() {
		return firstName;
	}

	public String getLastName() {
		return lastName;
	}

	public String getEmail() {
		return email;
	}

	public boolean isActive() {
		return active;
	}

	public Set<Expertise> getExpertiseAreas() {
		return expertiseAreas;
	}

	public List<Treatment> getTreatments() {
		return treatments;
	}

	public void addExpertise(Expertise expertise) {
		if (expertiseAreas.add(expertise)) {
			expertise.assignSpecialist(this);
		}
	}
}
