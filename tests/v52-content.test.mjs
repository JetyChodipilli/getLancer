import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync, readdirSync } from 'node:fs';
import { createHash } from 'node:crypto';
import { validateProjectMetadata, validateProjectBytes, verifyContent } from '../scripts/v52-content.mjs';

const projects = JSON.parse(readFileSync('education/projects.json', 'utf8'));
const records = JSON.parse(readFileSync('backend/src/main/resources/catalog/education.json', 'utf8'));
function bytes(index) {
  const project = projects[index], path = 'education/seeds/' + project.folder;
  const files = Object.fromEntries(readdirSync(path).filter(name => !name.startsWith('.')).map(name => {
    try { return [name, readFileSync(path + '/' + name)]; } catch (error) { if (error.code === 'EISDIR') return null; throw error; }
  }).filter(Boolean));
  return { project, record: records[index], files, archive: readFileSync('public/education/' + project.folder + '.zip') };
}
test('measured content inventory closes24components18corelabs12projects with3percategory and clean extraction', () => {
  const inventory = verifyContent();
  assert.deepEqual(inventory.counts, { components: 24, coreLabs: 18, projects: 12 });
  assert.deepEqual(inventory.projectCategories, { FULL_STACK: 3, DATA_ANALYTICS: 3, AI_ML: 3, IOT: 3 });
  assert.equal(inventory.projectsReproduced, 12);
  assert.equal(inventory.coreLabs.length, 18);
  assert.equal(new Set(inventory.projects.map(project => project.packageSha256)).size, 12);
  assert.ok(inventory.coreLabs.every(lab => lab.displayedMode === 'Recorded replay; not hosted live'));
});
test('count, category, distribution, source-only and metadata mutations fail closed', () => {
  validateProjectMetadata(projects, records);
  assert.throws(() => validateProjectMetadata(projects.slice(1), records), /Twelve/);
  assert.throws(() => validateProjectMetadata(projects, records.slice(1)), /count drift/);
  for (const [field, value] of [['category', 'AI_ML'], ['mode', 'PAID'], ['demoMode', 'LIVE'], ['demoUrl', 'https://misleading.invalid'], ['slug', 'stockroom']]) {
    const altered = structuredClone(projects); altered[4][field] = value;
    assert.throws(() => validateProjectMetadata(altered, records), undefined, field + ' drift must reject');
  }
  for (const mutate of [record => { record.previewPackageUrl = '/education/ai-ml.zip'; },
                         record => { record.release.snapshot.title = 'Changed'; },
                         record => { record.release.sourceHash = 'a'.repeat(64); },
                         record => { record.release.academic.shareAcademicDetails = true; }]) {
    const altered = structuredClone(records); mutate(altered[2]);
    assert.throws(() => validateProjectMetadata(projects, altered));
  }
});
test('package bytes, rights notices, dataset hashes and model hashes cannot drift unnoticed', () => {
  for (const index of [4, 6, 8]) {
    const { project, record, files, archive } = bytes(index);
    const hashes = validateProjectBytes(project, record, files, archive);
    assert.equal(hashes.LICENSE, createHash('sha256').update(files.LICENSE).digest('hex'));
    const corrupted = Buffer.from(archive); corrupted[20] ^= 1;
    assert.throws(() => validateProjectBytes(project, record, files, corrupted), /SHA-256/);
    assert.throws(() => validateProjectBytes(project, record, { ...files, NOTICE: Buffer.from('unreviewed') }, archive), /rights\/setup/);
    assert.throws(() => validateProjectBytes(project, record, { ...files, LICENSE: Buffer.from('Private proprietary licence'.repeat(6)) }, archive), /MIT/);
    assert.throws(() => validateProjectBytes(project, { ...record, packageAudit: { ...record.packageAudit, files: record.packageAudit.files.slice(1) } }, files, archive), /manifest drift/);
    if (project.dataAiEvidence) {
      const altered = structuredClone(project); altered.dataAiEvidence.dataSha256 = 'a'.repeat(64);
      assert.throws(() => validateProjectBytes(altered, record, files, archive), /Dataset/);
      if (project.category === 'AI_ML') {
        altered.dataAiEvidence.dataSha256 = project.dataAiEvidence.dataSha256; altered.dataAiEvidence.modelSha256 = 'b'.repeat(64);
        assert.throws(() => validateProjectBytes(altered, record, files, archive), /Model/);
        assert.throws(() => validateProjectBytes(project, record, { ...files, 'evaluation.json': files['synthetic.json'] }, archive), /not separate/);
      }
    }
  }
});
test('new full-stack sources include real local UI/API and persisted state, IoT stays simulation-only', () => {
  for (const index of [4, 5]) {
    const { files } = bytes(index);
    for (const name of ['index.html', 'client.mjs', 'style.css', 'server.mjs', 'test.mjs']) assert.ok(files[name]?.length);
    assert.match(files['server.mjs'].toString(), /writeFile.*JSON.stringify/);
    assert.match(files['server.mjs'].toString(), /rename/);
    assert.match(files['client.mjs'].toString(), /textContent/);
    assert.doesNotMatch(files['client.mjs'].toString(), /innerHTML/);
  }
  for (const project of projects.filter(project => project.category === 'IOT')) {
    assert.match(project.summary, /simulat/i);
    assert.match(project.categoryEvidence.boardFirmware, /no board|no MCU/i);
    assert.match(project.package.limitations.join(' '), /physical|physical board/i);
    assert.equal(project.demoMode, 'SOURCE_ONLY');
  }
});
