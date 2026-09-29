package com.deepblue.rescue.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "rescue_centers")
public class RescueCenter {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, unique = true, length = 50)
	private String code;

	@Column(nullable = false, length = 150)
	private String name;

	@Column(nullable = false, length = 100)
	private String city;

	@OneToMany(mappedBy = "rescueCenter")
	private List<RescueCase> cases = new ArrayList<>();

	protected RescueCenter() {
	}

	public RescueCenter(String code, String name, String city) {
		this.code = code;
		this.name = name;
		this.city = city;
	}

	public Long getId() {
		return id;
	}

	public String getCode() {
		return code;
	}

	public String getName() {
		return name;
	}

	public String getCity() {
		return city;
	}

	public List<RescueCase> getCases() {
		return cases;
	}

	public void addCase(RescueCase rescueCase) {
		if (!cases.contains(rescueCase)) {
			cases.add(rescueCase);
		}
		rescueCase.assignRescueCenter(this);
	}
}
