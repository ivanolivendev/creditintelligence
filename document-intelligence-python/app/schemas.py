from typing import Literal
from uuid import UUID

from pydantic import BaseModel, Field


class DocumentRef(BaseModel):
    documentId: UUID
    version: int = Field(gt=0)
    type: Literal["IDENTIFICATION", "INCOME_PROOF", "BANK_STATEMENT", "PAYSLIP"]
    period: str | None = None
    objectKey: str
    sha256: str = Field(pattern=r"^[0-9a-f]{64}$")


class ProcessRequest(BaseModel):
    contractVersion: Literal["1.0", "2.0"]
    analysisId: UUID
    attemptId: UUID
    documentRefs: list[DocumentRef]
    policyVersion: str
    correlationId: str


class ExtractedField(BaseModel):
    documentId: UUID
    version: int
    fieldName: str
    value: str | None
    currency: Literal["BRL"] | None
    extractionStatus: Literal["EXTRACTED", "MISSING", "AMBIGUOUS", "INVALID"]
    evidenceRef: str | None
    page: int | None
    snippet: str | None


class DocumentResult(BaseModel):
    documentId: UUID
    version: int
    fields: list[ExtractedField]
    transactions: list["ExtractedTransaction"] = Field(default_factory=list)


class ExtractedTransaction(BaseModel):
    documentId: UUID
    version: int
    transactionId: str
    postedAt: str
    direction: Literal["CREDIT", "DEBIT"]
    amount: str
    currency: Literal["BRL"]
    descriptionOriginal: str
    classification: Literal["POSSIBLE_SALARY", "RECURRING_PAYMENT", "UNKNOWN"]
    evidenceRef: str
    page: int
    snippet: str


class ProcessResponse(BaseModel):
    contractVersion: Literal["1.0", "2.0"] = "1.0"
    analysisId: UUID
    attemptId: UUID
    documentResults: list[DocumentResult]


class FindingContext(BaseModel):
    code: str
    result: str
    details: str
    evidenceRef: str | None


class ExplainRequest(BaseModel):
    contractVersion: Literal["1.0"]
    analysisId: UUID
    policyVersion: str
    policyChecksum: str
    policyText: str
    findings: list[FindingContext]
    allowedEvidenceRefs: list[str]
    allowedPolicyRefs: list[str]
    correlationId: str
    assessment: dict | None = None


class FindingExplanation(BaseModel):
    findingCode: str
    message: str
    policyReferences: list[str]
    evidenceReferences: list[str]


class ExplainResponse(BaseModel):
    contractVersion: Literal["1.0"] = "1.0"
    analysisId: UUID
    provider: Literal["MOCK", "BEDROCK"] = "MOCK"
    modelId: str = "deterministic-demo-v1"
    promptVersion: str = "1.0"
    summary: str
    explanations: list[FindingExplanation]
    limitations: list[str]
