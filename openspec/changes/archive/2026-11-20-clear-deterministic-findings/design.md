# Design: clear deterministic findings

## API context

The analysis response will expose the policy's income tolerance and required document types alongside the existing policy version. This gives the UI typed, version-bound values for explaining findings without parsing Java's diagnostic text.

## Presentation

- Translate finding statuses and document types to Portuguese labels.
- For income findings, compare declared and extracted values, show the absolute difference and policy tolerance, and cite the source document, page, and extracted snippet.
- For required documents, state whether requirements are complete and list the policy-required document types that are present or missing.
- Give an operational next step while stating that rule output supports, but does not replace, human review.
- Keep opaque evidence references available in a collapsed technical detail.

## Invariants

- Java remains the source of rule outcomes and policy criteria.
- UI text does not turn a warning into an approval or rejection.
- Missing and ambiguous extraction remains explicitly uncertain.
- Findings remain bound to the original policy and document snapshot.
