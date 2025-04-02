package com.abhinav.cc_backend_ai.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Entity(name = "AI_MASTER")
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AIMaster {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Integer pId;
	private String prompt;
	private String answer;
	private String timeIn;
	private String timeOut;
	private Long respTime;
}
