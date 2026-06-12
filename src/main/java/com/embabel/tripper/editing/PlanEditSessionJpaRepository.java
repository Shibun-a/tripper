package com.embabel.tripper.editing;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PlanEditSessionJpaRepository extends JpaRepository<PlanEditSessionEntity, String> {

    List<PlanEditSessionEntity> findAllByOrderByCreatedAtAsc();
}
