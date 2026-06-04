package com.embabel.tripper.rag;

import com.embabel.common.ai.prompt.PromptContributor;

import java.util.List;
import java.util.Locale;

public final class TravelKnowledgeContext implements PromptContributor {

    private final String query;
    private final List<TravelKnowledgeHit> hits;

    public TravelKnowledgeContext(String query, List<TravelKnowledgeHit> hits) {
        this.query = query;
        this.hits = List.copyOf(hits);
    }

    public static TravelKnowledgeContext empty() {
        return empty("");
    }

    public static TravelKnowledgeContext empty(String query) {
        return new TravelKnowledgeContext(query, List.of());
    }

    public String getQuery() {
        return query;
    }

    public List<TravelKnowledgeHit> getHits() {
        return hits;
    }

    public boolean isHasHits() {
        return !hits.isEmpty();
    }

    @Override
    public String contribution() {
        if (!isHasHits()) {
            return "No user-provided travel knowledge was retrieved for this trip.";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("User-provided travel knowledge retrieved for this trip.\n");
        sb.append("Use this context when relevant. Cite it inline using [KB:<citationId>].\n");
        sb.append("Do not cite a knowledge-base item unless it directly supports the recommendation.");

        for (TravelKnowledgeHit hit : hits) {
            sb.append("\n\n");
            sb.append("<knowledge-source citationId=\"")
                    .append(hit.getCitationId())
                    .append("\" score=\"")
                    .append(String.format(Locale.ROOT, "%.3f", hit.getScore()))
                    .append("\">\n");
            sb.append("Title: ").append(hit.getDocumentTitle()).append('\n');
            sb.append("Source type: ").append(hit.getSourceType()).append('\n');
            sb.append("Source: ").append(hit.getSource()).append('\n');
            sb.append("Matched terms: ").append(hit.getMatchedTermsText()).append('\n');
            sb.append(hit.getText()).append('\n');
            sb.append("</knowledge-source>");
        }

        return sb.toString();
    }
}
