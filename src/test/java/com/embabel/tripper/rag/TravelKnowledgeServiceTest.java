package com.embabel.tripper.rag;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TravelKnowledgeServiceTest {

    private TravelKnowledgeService service() {
        return new TravelKnowledgeService(
                new TravelKnowledgeRepository(),
                RestClient.create()
        );
    }

    @Test
    void indexesPastedTextAndRetrievesRelevantChunks() {
        TravelKnowledgeService service = service();
        service.addPastedText(
                "Bordeaux wine notes",
                "Bordeaux is known for wine, riverside walks, Saint-Emilion day trips, and relaxed vineyard routes."
        );

        var hits = service.search("relaxed Bordeaux wine vineyard route", 3);

        assertFalse(hits.isEmpty());
        assertEquals("Bordeaux wine notes", hits.getFirst().getDocumentTitle());
        assertTrue(hits.getFirst().getMatchedTerms().contains("bordeaux"));
        assertTrue(hits.getFirst().getCitationId().startsWith("Bordeaux wine notes#"));
    }

    @Test
    void buildsRetrievalContextFromQuery() {
        TravelKnowledgeService service = service();
        service.addPastedText(
                "Museum preference",
                "The travelers prefer history museums, medieval architecture, local markets, and countryside wine stops."
        );

        TravelKnowledgeContext context = service.retrieveForQuery(
                "Barcelona to Bordeaux driving trip with history museums, wine, markets, and countryside",
                6
        );

        assertTrue(context.isHasHits());
        assertTrue(context.contribution().contains("[KB:<citationId>]"));
        assertTrue(context.contribution().contains("Museum preference#1"));
    }
}
