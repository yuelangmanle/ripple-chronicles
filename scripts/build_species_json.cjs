const crypto = require('crypto');
const fs = require('fs');
const XLSX = require('xlsx');

const catalogWorkbook = XLSX.readFile('浮游动物目录.xlsx');
const photoWorkbook = XLSX.readFile('物种照片提取_分类整理.xlsx');

const headerTokens = [
  '四大类',
  '门',
  '亚门',
  '纲',
  '目',
  '科',
  '属',
  '种',
  '中文名',
  '拉丁名',
  'Class',
  'Order',
  'Family',
  'Genus',
  'Species'
];

function isHeaderRow(cells) {
  const hits = cells.filter((cell) => headerTokens.includes(cell)).length;
  return hits >= 2;
}

function toString(value) {
  return value == null ? '' : String(value).trim();
}

function parseSpecies(nameCn, nameLatin) {
  const raw = toString(nameCn);
  const latin = toString(nameLatin);
  if (!raw && !latin) return { nameCn: '', nameLatin: '' };
  const match = raw.match(/^(.*?)\s*\((.*?)\)$/);
  if (match) {
    return {
      nameCn: match[1].trim(),
      nameLatin: latin || match[2].trim()
    };
  }
  return { nameCn: raw, nameLatin: latin };
}

function buildId(parts) {
  return crypto.createHash('md5').update(parts.join('|')).digest('hex');
}

const speciesMap = new Map();

function addSpecies(entry) {
  const nameCn = toString(entry.nameCn);
  if (!nameCn) return;
  const nameLatin = toString(entry.nameLatin);
  const category = toString(entry.category) || '浮游动物';
  const key = nameCn;
  const existing = speciesMap.get(key);
  if (existing) {
    if (!existing.name_latin && nameLatin) {
      existing.name_latin = nameLatin;
    }
    if ((!existing.category || existing.category === '未分类') && category) {
      existing.category = category;
    }
    return;
  }
  speciesMap.set(key, {
    name_cn: nameCn,
    name_latin: nameLatin || null,
    category,
    source: entry.source || 'built-in'
  });
}

function ingestCatalogRows(rows) {
  let lastCategory = '';
  rows.forEach((row, index) => {
    const cells = Array.from({ length: 6 }, (_, idx) => toString(row[idx]));
    if (cells.every((cell) => !cell)) return;
    if ((index === 0 && isHeaderRow(cells)) || isHeaderRow(cells)) return;

    const categoryCell = cells[0];
    const speciesCell = cells[5];
    if (categoryCell) lastCategory = categoryCell;
    if (!speciesCell) return;

    const parsed = parseSpecies(speciesCell, '');
    if (!parsed.nameCn) return;

    addSpecies({
      nameCn: parsed.nameCn,
      nameLatin: parsed.nameLatin,
      category: '浮游动物',
      source: 'catalog'
    });
  });
}

function ingestPhotoRows(rows) {
  let lastPhylum = '';
  let lastSubphylum = '';
  rows.forEach((row, index) => {
    const cells = Array.from({ length: 8 }, (_, idx) => toString(row[idx]));
    if (cells.every((cell) => !cell)) return;
    if ((index === 0 && isHeaderRow(cells)) || isHeaderRow(cells)) return;

    const phylumCell = cells[0];
    const subphylumCell = cells[1];
    const nameCnCell = cells[6];
    const nameLatinCell = cells[7];

    if (phylumCell) lastPhylum = phylumCell;
    if (subphylumCell) lastSubphylum = subphylumCell;

    const parsed = parseSpecies(nameCnCell, nameLatinCell);
    if (!parsed.nameCn) return;

    addSpecies({
      nameCn: parsed.nameCn,
      nameLatin: parsed.nameLatin,
      category: '浮游动物',
      source: 'photo'
    });
  });
}

catalogWorkbook.SheetNames.forEach((name) => {
  const worksheet = catalogWorkbook.Sheets[name];
  const rows = XLSX.utils.sheet_to_json(worksheet, { header: 1, blankrows: false });
  ingestCatalogRows(rows);
});

photoWorkbook.SheetNames.forEach((name) => {
  const worksheet = photoWorkbook.Sheets[name];
  const rows = XLSX.utils.sheet_to_json(worksheet, { header: 1, blankrows: false });
  ingestPhotoRows(rows);
});

const species = Array.from(speciesMap.values()).map((entry) => ({
  id: buildId([entry.category || '', entry.name_cn || '', entry.name_latin || '']),
  name_cn: entry.name_cn,
  name_latin: entry.name_latin,
  category: entry.category,
  source: entry.source
}));

species.sort((a, b) => {
  const byCategory = (a.category || '').localeCompare(b.category || '');
  if (byCategory !== 0) return byCategory;
  return (a.name_cn || '').localeCompare(b.name_cn || '');
});

fs.writeFileSync(
  'app/src/main/res/raw/species_plankton.json',
  JSON.stringify(species, null, 2),
  'utf8'
);

console.log(`Wrote ${species.length} species to app/src/main/res/raw/species_plankton.json`);
