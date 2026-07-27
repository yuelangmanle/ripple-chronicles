const { createClient } = require('@supabase/supabase-js');
const XLSX = require('xlsx');

const SUPABASE_URL = process.env.SUPABASE_URL;
const SUPABASE_KEY = process.env.SUPABASE_SERVICE_ROLE_KEY;

if (!SUPABASE_URL || !SUPABASE_KEY) {
  throw new Error(
    'Missing SUPABASE_URL or SUPABASE_SERVICE_ROLE_KEY. Load them from a local .env file or environment manager.'
  );
}

const supabase = createClient(SUPABASE_URL, SUPABASE_KEY);

const headerTokens = [
  '四大类', '门', '亚门', '纲', '目', '科', '属', '种',
  '中文名', '拉丁名', 'Class', 'Order', 'Family', 'Genus', 'Species'
];

function isHeaderRow(cells) {
  return cells.filter((cell) => headerTokens.includes(cell)).length >= 2;
}

function toString(value) {
  return value == null ? '' : String(value).trim();
}

function parseSpecies(nameCn, nameLatin) {
  const raw = toString(nameCn);
  const latin = toString(nameLatin);
  if (!raw && !latin) return { nameCn: '', nameLatin: '' };
  const match = raw.match(/^(.*?)\s*\((.*?)\)$/);
  if (match) return { nameCn: match[1].trim(), nameLatin: latin || match[2].trim() };
  return { nameCn: raw, nameLatin: latin };
}

const speciesMap = new Map();

function addSpecies(entry) {
  const nameCn = toString(entry.nameCn);
  if (!nameCn) return;
  const nameLatin = toString(entry.nameLatin);
  const category = toString(entry.category) || '浮游动物';
  const existing = speciesMap.get(nameCn);
  if (existing) {
    if (!existing.name_latin && nameLatin) existing.name_latin = nameLatin;
    if ((!existing.category || existing.category === '未分类') && category) {
      existing.category = category;
    }
    return;
  }
  speciesMap.set(nameCn, {
    category,
    name_cn: nameCn,
    name_latin: nameLatin || null,
    source: entry.source || 'built-in'
  });
}

function ingestCatalogRows(rows) {
  rows.forEach((row) => {
    const cells = Array.from({ length: 6 }, (_, index) => toString(row[index]));
    if (cells.every((cell) => !cell) || isHeaderRow(cells)) return;
    const parsed = parseSpecies(cells[5], '');
    if (parsed.nameCn) {
      addSpecies({ ...parsed, category: '浮游动物', source: 'catalog' });
    }
  });
}

function ingestPhotoRows(rows) {
  rows.forEach((row) => {
    const cells = Array.from({ length: 8 }, (_, index) => toString(row[index]));
    if (cells.every((cell) => !cell) || isHeaderRow(cells)) return;
    const parsed = parseSpecies(cells[6], cells[7]);
    if (parsed.nameCn) {
      addSpecies({ ...parsed, category: '浮游动物', source: 'photo' });
    }
  });
}

async function importSpecies() {
  const catalogWorkbook = XLSX.readFile('浮游动物目录.xlsx');
  const photoWorkbook = XLSX.readFile('物种照片提取_分类整理.xlsx');

  catalogWorkbook.SheetNames.forEach((sheetName) => {
    const worksheet = catalogWorkbook.Sheets[sheetName];
    ingestCatalogRows(XLSX.utils.sheet_to_json(worksheet, { header: 1, blankrows: false }));
  });
  photoWorkbook.SheetNames.forEach((sheetName) => {
    const worksheet = photoWorkbook.Sheets[sheetName];
    ingestPhotoRows(XLSX.utils.sheet_to_json(worksheet, { header: 1, blankrows: false }));
  });

  const speciesToInsert = Array.from(speciesMap.values());
  console.log('Prepared ' + speciesToInsert.length + ' species for import.');

  const batchSize = 100;
  for (let i = 0; i < speciesToInsert.length; i += batchSize) {
    const batch = speciesToInsert.slice(i, i + batchSize);
    const { error } = await supabase.from('species').insert(batch);
    if (error) console.error('Error inserting batch:', error);
    else console.log('Inserted batch ' + (Math.floor(i / batchSize) + 1));
  }
  console.log('Import completed.');
}

importSpecies().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
