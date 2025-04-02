package com.abhinav.cc_backend_ai.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.abhinav.cc_backend_ai.model.AIMaster;

@Repository
public interface AIMasterRepository extends JpaRepository<AIMaster, Integer> {
	List<AIMaster> findBytimeInContains(String date);
}
