package com.creditintelligence.bureau.infrastructure;

import com.creditintelligence.bureau.application.BureauReport;
import com.creditintelligence.bureau.application.CreditBureauPort;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/** Local default: records the absence of a verified remote sandbox. */
@Component
@ConditionalOnProperty(name = "bureau.mode", havingValue = "mock", matchIfMissing = true)
public final class FixtureBureauAdapter implements CreditBureauPort {
    @Override
    public BureauReport consult(BureauQuery query) {
        if ("UNAVAILABLE".equals(query.caseName())) {
            return new BureauReport("FIXTURE", "CREDIT_BUREAU_PF_DEMO", "MOCK",
                    "UNAVAILABLE", null, "1.0", null, null, null,
                    List.of("Falha sintética da fixture; nenhuma consulta remota foi realizada."));
        }
        var complete = "COMPLETE".equals(query.caseName());
        return new BureauReport("FIXTURE", "CREDIT_BUREAU_PF_DEMO", "MOCK",
                "AVAILABLE", java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC), "1.0",
                null, complete ? new BureauReport.Score(640, 0, 1000, "SYNTHETIC_DEMO") : null,
                new BureauReport.Restrictions(complete ? 1 : 0),
                List.of("Fixture inteiramente sintética; nenhuma consulta remota foi realizada."));
    }
}
