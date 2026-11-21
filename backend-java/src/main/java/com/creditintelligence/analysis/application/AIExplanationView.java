package com.creditintelligence.analysis.application;

import com.creditintelligence.integration.documentintelligence.PythonExplanationClient.FindingExplanation;
import java.util.List;

public record AIExplanationView(String status, String provider, String modelId, String promptVersion,
        String summary, List<FindingExplanation> explanations, List<String> limitations) {
    public static AIExplanationView withoutContent(String status) {
        return new AIExplanationView(status, null, null, null, null, List.of(), List.of());
    }
}
