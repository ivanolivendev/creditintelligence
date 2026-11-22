# Design

Nginx serves the Vite build and proxies `/api/` to the private Java backend. The UI sends Basic credentials from in-memory state and never persists them. The workflow has four visible steps: connect, create or load a proposal, upload a digital synthetic PDF, analyze and decide. A decision is only enabled for a completed current snapshot. The UI labels the mock explanation and does not present it as a recommendation.
