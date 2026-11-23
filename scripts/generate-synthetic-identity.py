from pathlib import Path

from reportlab.lib import colors
from reportlab.lib.pagesizes import A4
from reportlab.lib.styles import getSampleStyleSheet, ParagraphStyle
from reportlab.lib.units import mm
from reportlab.pdfbase.ttfonts import TTFont
from reportlab.pdfbase import pdfmetrics
from reportlab.platypus import SimpleDocTemplate, Paragraph, Spacer, Table, TableStyle


ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / "output" / "pdf" / "identidade-sintetica-demo.pdf"
OUTPUT.parent.mkdir(parents=True, exist_ok=True)

font_path = Path("C:/Windows/Fonts/arial.ttf")
if font_path.exists():
    pdfmetrics.registerFont(TTFont("Arial", str(font_path)))
    body_font = "Arial"
else:
    body_font = "Helvetica"

doc = SimpleDocTemplate(
    str(OUTPUT), pagesize=A4, rightMargin=22 * mm, leftMargin=22 * mm,
    topMargin=20 * mm, bottomMargin=20 * mm,
)
styles = getSampleStyleSheet()
styles.add(ParagraphStyle(name="TitleCI", parent=styles["Title"], fontName=body_font, fontSize=22, leading=26, textColor=colors.HexColor("#152236"), spaceAfter=8))
styles.add(ParagraphStyle(name="LeadCI", parent=styles["Normal"], fontName=body_font, fontSize=10, leading=15, textColor=colors.HexColor("#667383"), spaceAfter=18))
styles.add(ParagraphStyle(name="LabelCI", parent=styles["Normal"], fontName=body_font, fontSize=8, leading=11, textColor=colors.HexColor("#00a89d"), tracking=1.2, spaceAfter=5))
styles.add(ParagraphStyle(name="BodyCI", parent=styles["Normal"], fontName=body_font, fontSize=10, leading=15, textColor=colors.HexColor("#26364a")))
styles.add(ParagraphStyle(name="NoteCI", parent=styles["Normal"], fontName=body_font, fontSize=8.5, leading=13, textColor=colors.HexColor("#667383")))

story = [
    Paragraph("DOCUMENTO DE IDENTIDADE", styles["LabelCI"]),
    Paragraph("Identidade sintética para demonstração", styles["TitleCI"]),
    Paragraph("Este documento é fictício e foi criado exclusivamente para testar o fluxo de análise documental do Credit Intelligence.", styles["LeadCI"]),
    Paragraph("IDENTIFICAÇÃO", styles["LabelCI"]),
]

data = [
    [Paragraph("Nome", styles["NoteCI"]), Paragraph("Maria E2E Sintética", styles["BodyCI"])],
    [Paragraph("Identificador", styles["NoteCI"]), Paragraph("DEMO-E2E-001", styles["BodyCI"])],
    [Paragraph("Tipo", styles["NoteCI"]), Paragraph("Identidade demonstrativa", styles["BodyCI"])],
    [Paragraph("Emissão", styles["NoteCI"]), Paragraph("21/11/2026", styles["BodyCI"])],
    [Paragraph("Validade", styles["NoteCI"]), Paragraph("31/12/2027", styles["BodyCI"])],
]
table = Table(data, colWidths=[42 * mm, 116 * mm], hAlign="LEFT")
table.setStyle(TableStyle([
    ("BACKGROUND", (0, 0), (-1, -1), colors.HexColor("#f4f7f8")),
    ("BOX", (0, 0), (-1, -1), 0.6, colors.HexColor("#d8e1e5")),
    ("INNERGRID", (0, 0), (-1, -1), 0.35, colors.HexColor("#d8e1e5")),
    ("VALIGN", (0, 0), (-1, -1), "MIDDLE"),
    ("LEFTPADDING", (0, 0), (-1, -1), 12), ("RIGHTPADDING", (0, 0), (-1, -1), 12),
    ("TOPPADDING", (0, 0), (-1, -1), 10), ("BOTTOMPADDING", (0, 0), (-1, -1), 10),
]))
story += [table, Spacer(1, 28), Paragraph("DECLARAÇÃO DE USO", styles["LabelCI"]), Paragraph("Não é um documento oficial, não comprova identidade civil e não deve ser utilizado fora do ambiente de demonstração. Todos os dados são sintéticos.", styles["BodyCI"]), Spacer(1, 18), Paragraph("Documento gerado para o ambiente local Credit Intelligence · Não contém dados pessoais reais.", styles["NoteCI"])]

doc.build(story)
print(OUTPUT)
