package com.embabel.tripper.rag;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TravelKnowledgeDocumentJpaRepository extends JpaRepository<TravelKnowledgeDocumentEntity, String> {

    List<TravelKnowledgeDocumentEntity> findAllByOrderByCreatedAtDesc();
}
