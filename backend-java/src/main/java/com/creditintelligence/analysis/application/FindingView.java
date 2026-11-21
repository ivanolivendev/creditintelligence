package com.creditintelligence.analysis.application;

public record FindingView(String code, String ruleVersion, String result, String details,
        String evidenceRef) {}
