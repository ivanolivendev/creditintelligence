package com.creditintelligence.bureau.application;

import java.util.UUID;

public interface CreditBureauPort {
    BureauReport consult(BureauQuery query);

    record BureauQuery(UUID proposalId, String caseName, String purpose) {}
}
