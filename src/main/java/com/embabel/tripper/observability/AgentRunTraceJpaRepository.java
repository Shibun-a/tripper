package com.embabel.tripper.observability;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AgentRunTraceJpaRepository extends JpaRepository<AgentRunTraceEntity, String> {

    List<AgentRunTraceEntity> findAllByOrderByCreatedAtDesc();

    List<AgentRunTraceEntity> findAllByOrderByCreatedAtAsc();

    /**
     * Row-locked read for read-modify-write mutations (append/complete/fail): two concurrent
     * action threads updating the same trace must serialize on the row, mirroring what the
     * in-memory store's synchronized methods guarantee.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from AgentRunTraceEntity t where t.runId = :runId")
    Optional<AgentRunTraceEntity> findForUpdate(@Param("runId") String runId);
}
