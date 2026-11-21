package com.creditintelligence.analysis.application;

import com.creditintelligence.integration.documentintelligence.PythonExplanationClient.FindingExplanation;
import com.creditintelligence.integration.documentintelligence.PythonExplanationClient.Hypothesis;
import java.util.List;

public record AIExplanationView(String status, String provider, String modelId, String promptVersion,
        String summary, List<FindingExplanation> explanations, List<Hypothesis> hypotheses,
        List<String> limitations) {
    public static AIExplanationView withoutContent(String status) {
        return new AIExplanationView(status, null, null, null, null, List.of(), List.of(), List.of());
    }
}
