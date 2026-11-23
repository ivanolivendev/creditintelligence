# DATA FLOW

1. Analyst creates a synthetic proposal in React.
2. Java validates and persists proposal metadata.
3. Analyst uploads digital PDF; Java validates signature/size/text, hashes and stores the object, then persists version metadata.
4. Java creates an immutable snapshot and analysis attempt with policy version and input fingerprint.
5. Java calls Python with document references, attempt identity and correlation data.
6. Python reads the object, extracts structured facts and evidence, and optionally generates a MOCK or Bedrock explanation.
7. Java validates contract version, snapshot membership, hashes, evidence and current lease.
8. Java runs deterministic financial rules and persists fields, findings and assessment.
9. The dossier exposes evidence, findings, assessment, provider status and limitations.
10. An authenticated analyst records a human decision tied to the analysis and snapshot.

Late attempts are rejected by lease/attempt checks. A new document version creates a new snapshot and preserves history.
