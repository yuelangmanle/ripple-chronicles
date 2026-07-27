const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '..');
const read = (relativePath) => fs.readFileSync(path.join(root, relativePath), 'utf8');
const fail = [];

const gradle = read('app/build.gradle');
const versionName = gradle.match(/versionName\s+"([^"]+)"/)?.[1];
const versionCode = gradle.match(/versionCode\s+(\d+)/)?.[1];
const applicationId = gradle.match(/applicationId\s+"([^"]+)"/)?.[1];

if (!versionName || !versionCode) fail.push('app/build.gradle 缺少 versionName 或 versionCode');
if (applicationId !== 'com.dlovel') fail.push(`包名必须保持 com.dlovel，当前为 ${applicationId || '空'}`);

const versionReferences = [
  'CHANGELOG.md',
  '更新日志.md',
  '项目说明书.md',
  '开发进展书.md',
  'app/src/main/res/raw/changelog.md'
];
for (const file of versionReferences) {
  if (!read(file).includes(versionName)) {
    fail.push(`${file} 未包含当前版本 ${versionName}`);
  }
}

const releaseLinks = read('app/src/main/java/com/dlovel/plankton/util/ReleaseLinks.kt');
if (!releaseLinks.includes('yuelangmanle/ripple-chronicles')) {
  fail.push('ReleaseLinks 未指向公开仓库 yuelangmanle/ripple-chronicles');
}

const encryptedDocuments = [
  'app/src/main/res/raw/project_spec.enc',
  'app/src/main/res/raw/dev_progress.enc'
];
for (const file of encryptedDocuments) {
  if (!fs.existsSync(path.join(root, file))) fail.push(`缺少加密文档资源：${file}`);
}
const plaintextDocuments = [
  'app/src/main/res/raw/project_spec.md',
  'app/src/main/res/raw/dev_progress.md'
];
for (const file of plaintextDocuments) {
  if (fs.existsSync(path.join(root, file))) fail.push(`不应提交明文文档资源：${file}`);
}

const expectedTag = process.env.RELEASE_TAG;
if (expectedTag && expectedTag !== `v${versionName}`) {
  fail.push(`Release tag ${expectedTag} 与 versionName ${versionName} 不一致`);
}

const releaseApk = process.env.RELEASE_APK;
if (releaseApk && !fs.existsSync(releaseApk)) {
  fail.push(`Release APK 不存在：${releaseApk}`);
}

if (fail.length) {
  console.error(fail.map(message => `- ${message}`).join('\n'));
  process.exit(1);
}

console.log(`Release consistency OK: com.dlovel v${versionName} (${versionCode})`);
