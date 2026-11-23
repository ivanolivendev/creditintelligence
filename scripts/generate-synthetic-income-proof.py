from pathlib import Path

from reportlab.lib import colors
from reportlab.lib.pagesizes import A4
from reportlab.lib.styles import getSampleStyleSheet, ParagraphStyle
from reportlab.lib.units import mm
from reportlab.pdfbase.ttfonts import TTFont
from reportlab.pdfbase import pdfmetrics
from reportlab.platypus import SimpleDocTemplate, Paragraph, Spacer, Table, TableStyle


ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / "output" / "pdf" / "comprovante-renda-sintetico-demo.pdf"
OUTPUT.parent.mkdir(parents=True, exist_ok=True)

font_path = Path("C:/Windows/Fonts/arial.ttf")
if font_path.exists():
    pdfmetrics.registerFont(TTFont("Arial", str(font_path)))
    body_font = "Arial"
else:
    body_font = "Helvetica"

doc = SimpleDocTemplate(str(OUTPUT), pagesize=A4, rightMargin=22 * mm, leftMargin=22 * mm, topMargin=20 * mm, bottomMargin=20 * mm)
styles = getSampleStyleSheet()
styles.add(ParagraphStyle(name="TitleCI", parent=styles["Title"], fontName=body_font, fontSize=22, leading=26, textColor=colors.HexColor("#152236"), spaceAfter=8))
styles.add(ParagraphStyle(name="LeadCI", parent=styles["Normal"], fontName=body_font, fontSize=10, leading=15, textColor=colors.HexColor("#667383"), spaceAfter=18))
styles.add(ParagraphStyle(name="LabelCI", parent=styles["Normal"], fontName=body_font, fontSize=8, leading=11, textColor=colors.HexColor("#00a89d"), tracking=1.2, spaceAfter=5))
styles.add(ParagraphStyle(name="BodyCI", parent=styles["Normal"], fontName=body_font, fontSize=10, leading=15, textColor=colors.HexColor("#26364a")))
styles.add(ParagraphStyle(name="NoteCI", parent=styles["Normal"], fontName=body_font, fontSize=8.5, leading=13, textColor=colors.HexColor("#667383")))

story = [
    Paragraph("COMPROVANTE DE RENDA", styles["LabelCI"]),
    Paragraph("Declaração de renda mensal", styles["TitleCI"]),
    Paragraph("Documento sintético criado exclusivamente para testar a extração e validação documental do Credit Intelligence.", styles["LeadCI"]),
    Paragraph("DADOS DECLARADOS", styles["LabelCI"]),
]

data = [
    [Paragraph("Nome", styles["NoteCI"]), Paragraph("Maria E2E Sintética", styles["BodyCI"])],
    [Paragraph("Identificador", styles["NoteCI"]), Paragraph("DEMO-E2E-001", styles["BodyCI"])],
    [Paragraph("Competência", styles["NoteCI"]), Paragraph("Outubro de 2026", styles["BodyCI"])],
    [Paragraph("Renda mensal líquida", styles["NoteCI"]), Paragraph("R$ 7.500,00", styles["BodyCI"])],
    [Paragraph("Moeda", styles["NoteCI"]), Paragraph("BRL", styles["BodyCI"])],
]
table = Table(data, colWidths=[55 * mm, 103 * mm], hAlign="LEFT")
table.setStyle(TableStyle([
    ("BACKGROUND", (0, 0), (-1, -1), colors.HexColor("#f4f7f8")),
    ("BOX", (0, 0), (-1, -1), 0.6, colors.HexColor("#d8e1e5")),
    ("INNERGRID", (0, 0), (-1, -1), 0.35, colors.HexColor("#d8e1e5")),
    ("VALIGN", (0, 0), (-1, -1), "MIDDLE"),
    ("LEFTPADDING", (0, 0), (-1, -1), 12), ("RIGHTPADDING", (0, 0), (-1, -1), 12),
    ("TOPPADDING", (0, 0), (-1, -1), 10), ("BOTTOMPADDING", (0, 0), (-1, -1), 10),
]))
story += [table, Spacer(1, 28), Paragraph("OBSERVAÇÃO", styles["LabelCI"]), Paragraph("Este comprovante é fictício, não é emitido por empregador ou instituição financeira e não comprova renda real. Todos os dados foram criados para demonstração local.", styles["BodyCI"]), Spacer(1, 18), Paragraph("Credit Intelligence · Dados exclusivamente sintéticos · Documento sem validade oficial", styles["NoteCI"])]

doc.build(story)
print(OUTPUT)
