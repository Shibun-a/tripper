package com.embabel.tripper.verification;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PlanVerificationResultJpaRepository extends JpaRepository<PlanVerificationResultEntity, String> {

    List<PlanVerificationResultEntity> findAllByOrderByCreatedAtDesc();

    List<PlanVerificationResultEntity> findAllByOrderByCreatedAtAsc();
}
