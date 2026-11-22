# Design

Java locks the proposal row before reading the current document snapshot. The input fingerprint includes proposal, declared income, policy checksum and document snapshot hash. `analysis_request_key` has `(proposal_id, request_key)` as primary key and binds the fingerprint and analysis ID. The transaction commits `RUNNING` before the Python call. A repeated request with the same key returns or retries the bound analysis; a changed fingerprint returns 409. The proposal lock serializes concurrent starts and document uploads.

A retry increments `attempt`, replaces `lease_token`, and clears the previous error. Completion and failure lock the analysis row and compare the lease token. After an expired lease, the next caller may take over. A late response from the old lease cannot write fields or findings. The HTTP response remains synchronous, and a crash can leave `RUNNING` until a later request recovers it.
