from uuid import uuid4

from fastapi.testclient import TestClient

from app import main
from app.main import app


client = TestClient(app)


def process_payload() -> dict:
    return {
        "contractVersion": "1.0",
        "analysisId": str(uuid4()),
        "attemptId": str(uuid4()),
        "documentRefs": [],
        "policyVersion": "POLICY_V3",
        "correlationId": "http-test",
    }


def explain_payload() -> dict:
    return {
        "contractVersion": "1.0",
        "analysisId": str(uuid4()),
        "policyVersion": "POLICY_V3",
        "policyChecksum": "checksum",
        "policyText": "POLICY_V3 deterministic rules",
        "findings": [
            {
                "code": "INCOME_CONSISTENCY",
                "result": "PASS",
                "details": "synthetic evidence",
                "evidenceRef": "doc-1:p1",
            }
        ],
        "allowedEvidenceRefs": ["doc-1:p1"],
        "allowedPolicyRefs": ["POLICY_V3:INCOME_CONSISTENCY"],
        "allowedTransactionRefs": [],
        "correlationId": "http-test",
    }


def test_health_exposes_provider_without_auth() -> None:
    response = client.get("/health")

    assert response.status_code == 200
    assert response.json()["status"] == "UP"


def test_process_documents_requires_internal_token(monkeypatch) -> None:
    monkeypatch.setenv("INTERNAL_SERVICE_TOKEN", "expected-token")

    response = client.post("/internal/v1/process-documents", json=process_payload())

    assert response.status_code == 403
    assert response.json()["detail"] == "Internal access denied"


def test_process_documents_validates_http_contract(monkeypatch) -> None:
    monkeypatch.setenv("INTERNAL_SERVICE_TOKEN", "expected-token")

    response = client.post(
        "/internal/v1/process-documents",
        headers={"X-Internal-Token": "expected-token"},
        json={**process_payload(), "contractVersion": "9.9"},
    )

    assert response.status_code == 422


def test_process_documents_returns_structured_response(monkeypatch) -> None:
    monkeypatch.setenv("INTERNAL_SERVICE_TOKEN", "expected-token")
    monkeypatch.setattr(main, "extract_from_storage", lambda ref: None)

    response = client.post(
        "/internal/v1/process-documents",
        headers={"X-Internal-Token": "expected-token"},
        json=process_payload(),
    )

    assert response.status_code == 200
    assert response.json()["documentResults"] == []


def test_explain_mock_uses_only_allowed_references(monkeypatch) -> None:
    monkeypatch.setenv("INTERNAL_SERVICE_TOKEN", "expected-token")
    monkeypatch.setenv("AI_PROVIDER", "mock")

    response = client.post(
        "/internal/v1/explain",
        headers={"X-Internal-Token": "expected-token"},
        json=explain_payload(),
    )

    assert response.status_code == 200
    body = response.json()
    assert body["provider"] == "MOCK"
    assert body["explanations"][0]["policyReferences"] == [
        "POLICY_V3:INCOME_CONSISTENCY"
    ]
    assert body["explanations"][0]["evidenceReferences"] == ["doc-1:p1"]


def test_explain_rejects_policy_version_mismatch(monkeypatch) -> None:
    monkeypatch.setenv("INTERNAL_SERVICE_TOKEN", "expected-token")

    response = client.post(
        "/internal/v1/explain",
        headers={"X-Internal-Token": "expected-token"},
        json={**explain_payload(), "policyText": "POLICY_V2 old rules"},
    )

    assert response.status_code == 422
    assert response.json()["detail"] == "Policy version mismatch"


def test_explain_rejects_unknown_provider(monkeypatch) -> None:
    monkeypatch.setenv("INTERNAL_SERVICE_TOKEN", "expected-token")
    monkeypatch.setenv("AI_PROVIDER", "unknown")

    response = client.post(
        "/internal/v1/explain",
        headers={"X-Internal-Token": "expected-token"},
        json=explain_payload(),
    )

    assert response.status_code == 503
    assert response.json()["detail"] == "Configured AI provider unavailable"
