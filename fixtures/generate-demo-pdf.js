// Generates a small, selectable-text PDF using synthetic demonstration data.
const fs = require('node:fs');
const path = require('node:path');

function generate(output, stream) {
  const objects = [
    '<< /Type /Catalog /Pages 2 0 R >>',
    '<< /Type /Pages /Kids [3 0 R] /Count 1 >>',
    '<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Resources << /Font << /F1 4 0 R >> >> /Contents 5 0 R >>',
    '<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>',
    `<< /Length ${Buffer.byteLength(stream, 'ascii')} >>\nstream\n${stream}\nendstream`,
  ];

  let pdf = '%PDF-1.4\n';
  const offsets = [0];
  for (let index = 0; index < objects.length; index++) {
    offsets.push(Buffer.byteLength(pdf, 'ascii'));
    pdf += `${index + 1} 0 obj\n${objects[index]}\nendobj\n`;
  }
  const xrefOffset = Buffer.byteLength(pdf, 'ascii');
  pdf += `xref\n0 ${objects.length + 1}\n0000000000 65535 f \n`;
  for (const offset of offsets.slice(1)) {
    pdf += `${String(offset).padStart(10, '0')} 00000 n \n`;
  }
  pdf += `trailer\n<< /Size ${objects.length + 1} /Root 1 0 R >>\nstartxref\n${xrefOffset}\n%%EOF\n`;

  fs.mkdirSync(path.dirname(output), { recursive: true });
  fs.writeFileSync(output, pdf, 'ascii');
  console.log(output);
}

generate(path.join(__dirname, 'documents', 'income-proof-demo.pdf'),
  'BT /F1 18 Tf 72 720 Td (Renda mensal: R$ 7.500,00) Tj ET');
generate(path.join(__dirname, 'documents', 'blank-demo.pdf'), '');
generate(path.join(__dirname, 'documents', 'ambiguous-income-demo.pdf'),
  'BT /F1 18 Tf 72 720 Td (Renda mensal: R$ 7.500,00) Tj 0 -28 Td (Renda mensal: R$ 8.000,00) Tj ET');

function linesPdf(filename, lines) {
  const stream = 'BT /F1 12 Tf 72 720 Td ' + lines.map((line, index) =>
    `${index ? '0 -24 Td ' : ''}(${line}) Tj`).join(' ') + ' ET';
  generate(path.join(__dirname, 'documents', filename), stream);
}

for (const [month, gross, net] of [
  ['2026-08', '9000.00', '7300.00'],
  ['2026-09', '10200.00', '8500.00'],
  ['2026-10', '9100.00', '7400.00'],
]) {
  linesPdf(`payslip-${month}-demo.pdf`, [
    'DEMONSTRACAO - DADOS FICTICIOS', `Competencia: ${month}`,
    `Salario bruto: BRL ${gross}`, `Salario liquido: BRL ${net}`,
    'Desconto consignado: BRL 300.00',
  ]);
  linesPdf(`statement-${month}-demo.pdf`, [
    'DEMONSTRACAO - DADOS FICTICIOS', `Periodo: ${month}`,
    `${month}-28 | CREDIT | BRL ${net} | SALARIO DEMO ACME`,
    `${month}-29 | DEBIT | BRL 300.00 | PARCELA DEMO CONSIGNADO`,
    ...(month === '2026-10' ? [`${month}-30 | CREDIT | BRL 4000.00 | PIX DEMO DESCONHECIDO`] : []),
  ]);
}
