const fs = require('fs');
const crypto = require('crypto');
const path = require('path');
const root = path.resolve(__dirname, '..');
const blobs = {
  'canonical/upstream-skill.md': '766e92834c92566eecb01e2af19a22b2ae0fd485',
  'agents/ultra-implementer.md': 'a3fd4b3f08ac1abfcda0af7de33315d24b13a598',
  'agents/ultra-reviewer.md': '1865eda5dabdff3eaef58341d11cfa9828325123',
  'agents/ultra-skeptic.md': '8caf0c0e48f6b70982141b7ca44dd48633901c20',
  'scripts/ultra.bb': '542921aa35249c518c4ab08935b14ca3b6e2c637',
  'workflows/template-review.edn': 'b559d04069f90ffcec18d09f736ca319c6b98af7'
};
for (const [file, expected] of Object.entries(blobs)) {
  const bytes = fs.readFileSync(path.join(root, file));
  const got = crypto.createHash('sha1').update(`blob ${bytes.length}\0`).update(bytes).digest('hex');
  if (got !== expected) throw new Error(`source mismatch: ${file}`);
}
const skill = fs.readFileSync(path.join(root,'SKILL.md'),'utf8');
for (const ref of ['references/INSTRUCTIONS.md','CONTRACT.edn','configs/qualification.edn']) {
  if (!skill.includes(ref) || !fs.existsSync(path.join(root,ref))) throw new Error(`missing reference: ${ref}`);
}
console.log('Source blob identities and compatibility references verified; runtime behavior not tested.');
