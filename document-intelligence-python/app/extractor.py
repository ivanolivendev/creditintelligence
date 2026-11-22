import hashlib
import re
from decimal import Decimal, InvalidOperation
from pathlib import Path

from pypdf import PdfReader

from app.schemas import DocumentRef, DocumentResult, ExtractedField, ExtractedTransaction

INCOME_PATTERN = re.compile(
    r"(?:renda\s+mensal|monthly\s+income)\s*:\s*(?:R\$|BRL)\s*([0-9][0-9.,]*)",
    re.IGNORECASE,
)
KEY_PATTERN = re.compile(
    r"^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}/"
    r"[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\.pdf$"
)
MONEY_PATTERN = r"(?:R\$|BRL)\s*([0-9][0-9.,]*)"
PAYSLIP_PATTERNS = {
    "competence": re.compile(r"^Competencia:\s*(\d{4}-\d{2})$", re.IGNORECASE),
    "grossSalary": re.compile(rf"^Salario bruto:\s*{MONEY_PATTERN}$", re.IGNORECASE),
    "netSalary": re.compile(rf"^Salario liquido:\s*{MONEY_PATTERN}$", re.IGNORECASE),
    "payrollDeduction": re.compile(rf"^Desconto consignado:\s*{MONEY_PATTERN}$", re.IGNORECASE),
}
STATEMENT_PERIOD = re.compile(r"^Periodo:\s*(\d{4}-\d{2})$", re.IGNORECASE)
TRANSACTION = re.compile(
    rf"^(\d{{4}}-\d{{2}}-\d{{2}})\s*\|\s*(CREDIT|DEBIT)\s*\|\s*{MONEY_PATTERN}"
    r"\s*\|\s*(.{1,100})$", re.IGNORECASE)


class DocumentReferenceError(ValueError):
    pass


def _decimal(raw: str) -> Decimal:
    if "," in raw:
        normalized = raw.replace(".", "").replace(",", ".")
    else:
        normalized = raw
    try:
        return Decimal(normalized).quantize(Decimal("0.01"))
    except InvalidOperation as exc:
        raise DocumentReferenceError("Invalid monetary value") from exc


def _field(ref: DocumentRef, status: str, value: str | None = None,
           page: int | None = None, snippet: str | None = None,
           name: str = "monthlyIncome", currency: str | None = None) -> ExtractedField:
    evidence = f"{ref.documentId}:v{ref.version}:p{page}:{name}" if page else None
    return ExtractedField(
        documentId=ref.documentId,
        version=ref.version,
        fieldName=name,
        value=value,
        currency=currency if value is not None else None,
        extractionStatus=status,
        evidenceRef=evidence,
        page=page,
        snippet=snippet,
    )


def extract_document(ref: DocumentRef, root: Path) -> DocumentResult:
    if not KEY_PATTERN.fullmatch(ref.objectKey):
        raise DocumentReferenceError("Invalid object key")
    resolved_root = root.resolve()
    file_path = (resolved_root / ref.objectKey).resolve()
    if not file_path.is_relative_to(resolved_root) or not file_path.is_file():
        raise DocumentReferenceError("Document object unavailable")
    content = file_path.read_bytes()
    if hashlib.sha256(content).hexdigest() != ref.sha256:
        raise DocumentReferenceError("Document checksum mismatch")
    if ref.type not in ("INCOME_PROOF", "PAYSLIP", "BANK_STATEMENT"):
        return DocumentResult(documentId=ref.documentId, version=ref.version, fields=[])

    try:
        reader = PdfReader(file_path)
        lines = [(page_number, line.strip()[:240])
                 for page_number, page in enumerate(reader.pages, start=1)
                 for line in (page.extract_text() or "").splitlines() if line.strip()]
        if ref.type == "PAYSLIP":
            fields = []
            for name, pattern in PAYSLIP_PATTERNS.items():
                found = [(match.group(1), page, line)
                         for page, line in lines if (match := pattern.fullmatch(line))]
                if not found:
                    fields.append(_field(ref, "MISSING", name=name))
                elif len({value for value, _, _ in found}) != 1:
                    fields.append(_field(ref, "AMBIGUOUS", name=name))
                else:
                    raw, page, line = found[0]
                    value = raw if name == "competence" else f"{_decimal(raw):.2f}"
                    fields.append(_field(ref, "EXTRACTED", value, page, line, name,
                                         None if name == "competence" else "BRL"))
            return DocumentResult(documentId=ref.documentId, version=ref.version,
                                  fields=fields)
        if ref.type == "BANK_STATEMENT":
            found = [(match.group(1), page, line) for page, line in lines
                     if (match := STATEMENT_PERIOD.fullmatch(line))]
            if not found:
                period = _field(ref, "MISSING", name="statementPeriod")
            elif len({value for value, _, _ in found}) != 1:
                period = _field(ref, "AMBIGUOUS", name="statementPeriod")
            else:
                value, page, line = found[0]
                period = _field(ref, "EXTRACTED", value, page, line, "statementPeriod")
            transactions = []
            for page, line in lines:
                match = TRANSACTION.fullmatch(line)
                if not match:
                    continue
                posted, direction, raw_amount, description = match.groups()
                index = len(transactions) + 1
                description = description.strip()
                classification = ("POSSIBLE_SALARY" if direction.upper() == "CREDIT"
                                  and description.upper().startswith("SALARIO DEMO")
                                  else "RECURRING_PAYMENT" if direction.upper() == "DEBIT"
                                  and description.upper().startswith("PARCELA DEMO")
                                  else "UNKNOWN")
                transactions.append(ExtractedTransaction(
                    documentId=ref.documentId, version=ref.version,
                    transactionId=f"tx{index}", postedAt=posted,
                    direction=direction.upper(), amount=f"{_decimal(raw_amount):.2f}",
                    currency="BRL", descriptionOriginal=description,
                    classification=classification,
                    evidenceRef=f"{ref.documentId}:v{ref.version}:p{page}:tx{index}",
                    page=page, snippet=line))
            return DocumentResult(documentId=ref.documentId, version=ref.version,
                                  fields=[period], transactions=transactions)
        matches: list[tuple[Decimal, int, str]] = []
        for page_number, line in lines:
            for match in INCOME_PATTERN.finditer(line):
                matches.append((_decimal(match.group(1)), page_number, line))
    except Exception:
        invalid_name = ("netSalary" if ref.type == "PAYSLIP" else
                        "statementPeriod" if ref.type == "BANK_STATEMENT" else "monthlyIncome")
        return DocumentResult(documentId=ref.documentId, version=ref.version,
                              fields=[_field(ref, "INVALID", name=invalid_name)])

    if not matches:
        field = _field(ref, "MISSING")
    elif len({amount for amount, _, _ in matches}) > 1:
        field = _field(ref, "AMBIGUOUS")
    else:
        amount, page, snippet = matches[0]
        field = _field(ref, "EXTRACTED", f"{amount:.2f}", page, snippet,
                       currency="BRL")
    return DocumentResult(documentId=ref.documentId, version=ref.version, fields=[field])
