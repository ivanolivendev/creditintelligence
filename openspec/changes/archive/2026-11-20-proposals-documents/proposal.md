# Proposal

## Why

The Compose baseline starts, but an analyst cannot create a proposal or attach evidence. This change establishes the first persistent, authenticated path required by the Blueprint V3.

## What Changes

- Create and retrieve synthetic credit proposals with validated monetary fields.
- Receive digital PDF documents, verify the file, store a private immutable version, and return its checksum and metadata.
- List document versions for a proposal and reject invalid uploads without creating metadata.

## Capabilities

### New Capabilities

- `proposal-management`: Create and retrieve synthetic proposals through an authenticated API.
- `document-ingestion`: Accept and version PDF evidence linked to a proposal.

### Modified Capabilities

None.

## Impact

Java proposal and document modules, PostgreSQL/Flyway, private local object storage, Compose volume, REST contracts, and API/integration tests. Python and Bedrock behavior is unchanged in this slice.
