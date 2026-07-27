const crypto = require('crypto');
const fs = require('fs');
const path = require('path');

const password = process.env.DOC_PASSWORD;
if (!password) {
  throw new Error('DOC_PASSWORD is required and must never be committed.');
}

const sourceDir = process.env.DOC_SOURCE_DIR || process.cwd();
const outputDir = path.join(process.cwd(), 'app', 'src', 'main', 'res', 'raw');
const iterations = 120000;

function encryptDocument(inputName, outputName) {
  const source = fs.readFileSync(path.join(sourceDir, inputName));
  const salt = crypto.randomBytes(16);
  const iv = crypto.randomBytes(12);
  const key = crypto.pbkdf2Sync(password, salt, iterations, 32, 'sha256');
  const cipher = crypto.createCipheriv('aes-256-gcm', key, iv);
  const ciphertext = Buffer.concat([cipher.update(source), cipher.final()]);
  const payload = {
    version: 1,
    algorithm: 'AES-256-GCM',
    kdf: 'PBKDF2-HMAC-SHA256',
    iterations,
    salt: salt.toString('base64'),
    iv: iv.toString('base64'),
    ciphertext: ciphertext.toString('base64'),
    authTag: cipher.getAuthTag().toString('base64')
  };
  fs.writeFileSync(
    path.join(outputDir, outputName),
    JSON.stringify(payload),
    'utf8'
  );
}

encryptDocument('项目说明书.md', 'project_spec.enc');
encryptDocument('开发进展书.md', 'dev_progress.enc');
console.log('Encrypted project documents into Android raw resources.');
