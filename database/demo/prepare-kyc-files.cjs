// Original, plainly labelled test PDF; no real credential or identity is reproduced.
const fs = require('node:fs');
const path = require('node:path');
const root = path.resolve(__dirname, '../..');
const env = Object.fromEntries(fs.readFileSync(path.join(root, '.env'),'utf8').split(/\r?\n/)
  .map(l => l.match(/^([A-Z0-9_]+)=(.*)$/)).filter(Boolean).map(m => [m[1],m[2].trim().replace(/^['"]|['"]$/g,'')]));
const storage = path.resolve(root, env.STORAGE_CREDENTIALS_ROOT || env.EDUFIT_STORAGE_CREDENTIALS_ROOT || 'var/credentials');
const relative = path.relative(root,storage);
if(relative.startsWith('..') || path.isAbsolute(relative)) throw new Error('Demo files must stay inside this checkout');
const content = [
  'BT /F1 22 Tf 55 750 Td (EDUFIT DEMO KYC) Tj',
  '0 -45 Td /F1 16 Tf (NOT A REAL CREDENTIAL) Tj',
  '0 -42 Td /F1 12 Tf (Synthetic sample for the EduFit defense presentation only.) Tj',
  '0 -28 Td (No real identity, university, degree or certificate is represented.) Tj',
  '0 -28 Td (Use the Admin queue to demonstrate viewing, approval and rejection.) Tj',
  '0 -60 Td /F1 18 Tf (SAMPLE - DEMO ONLY) Tj ET'
].join('\n');
const objects = [
  '<< /Type /Catalog /Pages 2 0 R >>',
  '<< /Type /Pages /Count 1 /Kids [3 0 R] >>',
  '<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Resources << /Font << /F1 4 0 R >> >> /Contents 5 0 R >>',
  '<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>',
  `<< /Length ${Buffer.byteLength(content)} >>\nstream\n${content}\nendstream`
];
let pdf='%PDF-1.4\n'; const offsets=[0];
objects.forEach((obj,i)=>{offsets.push(Buffer.byteLength(pdf));pdf+=`${i+1} 0 obj\n${obj}\nendobj\n`;});
const xref=Buffer.byteLength(pdf);
pdf+=`xref\n0 6\n0000000000 65535 f \n`+offsets.slice(1).map(n=>String(n).padStart(10,'0')+' 00000 n \n').join('');
pdf+=`trailer\n<< /Size 6 /Root 1 0 R >>\nstartxref\n${xref}\n%%EOF\n`;
const folder=path.join(storage,'enrichment-20261110'); fs.mkdirSync(folder,{recursive:true});
for(let i=1;i<=10;i++) fs.writeFileSync(path.join(folder,`kyc-${String(i).padStart(2,'0')}.pdf`),pdf);
console.log(JSON.stringify({files:10,bytes:Buffer.byteLength(pdf),storage:folder}));
