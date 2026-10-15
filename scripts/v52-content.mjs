/** Measured source/rights inventory. Native core-lab execution is a separate CI gate. */
import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { readFileSync, readdirSync, lstatSync } from 'node:fs';
import { execFileSync } from 'node:child_process';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { parseScenarioRecording } from '../lib/scenario-evidence.ts';

const repository = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const hash = bytes => createHash('sha256').update(bytes).digest('hex');
const categories = ['FULL_STACK', 'DATA_ANALYTICS', 'AI_ML', 'IOT'];
const evidenceKeys = {
  FULL_STACK: ['modules', 'schemaApi', 'demoAccounts', 'setupMigrations', 'versions', 'tests', 'deployment'],
  DATA_ANALYTICS: ['dataSourceLicense', 'sampleSchema', 'transformations', 'notebook', 'charts', 'interpretation', 'reproducibility'],
  AI_ML: ['task', 'datasetModelLicense', 'splits', 'evaluation', 'metrics', 'inference', 'limitations'],
  IOT: ['boardFirmware', 'billOfMaterials', 'wiring', 'powerConnectivity', 'topics', 'evidence'],
};
function canonical(value) {
  if (Array.isArray(value)) return value.map(canonical);
  if (value && typeof value === 'object') return Object.fromEntries(Object.keys(value).sort().map(key => [key, canonical(value[key])]));
  return value;
}

export function validateProjectMetadata(projects, records) {
  assert.equal(projects.length, 12, 'Twelve reviewed original projects required');
  assert.equal(records.length, 12, 'Generated project count drift');
  for (const key of ['slug', 'folder']) assert.equal(new Set(projects.map(item => item[key])).size, 12, `Duplicate project ${key}`);
  for (const category of categories) assert.equal(projects.filter(item => item.category === category).length, 3, `Three ${category} projects required`);
  for (const [index, project] of projects.entries()) {
    assert.match(project.folder, /^[a-z][a-z0-9-]{0,50}$/, 'Bounded local source folder required');
    assert.match(project.slug, /^[a-z][a-z0-9-]{0,50}$/, 'Bounded public project slug required');
    assert(['FREE', 'PAID', 'SHOWCASE'].includes(project.mode), 'Unknown distribution mode');
    assert(['JavaScript', 'Python'].includes(project.language), 'Reviewed project runtime required');
    assert.equal(project.demoMode, 'SOURCE_ONLY'); assert.equal(project.demoUrl, '', 'Source-only projects cannot claim a hosted URL');
    const record = records[index], snapshot = record.release.snapshot;
    assert.equal(record.productSlug, project.slug); assert.equal(record.title, project.title); assert.equal(record.summary, project.summary);
    assert.equal(record.illustrative, true); assert.equal(record.release.status, 'APPROVED');
    assert.equal(record.release.id, `10000000-0000-4000-8000-${String(index + 1).padStart(12, '0')}`);
    assert.equal(record.previewPackageUrl, project.mode === 'FREE' ? `/education/${project.folder}.zip` : null, 'Paid/showcase public source offer drift');
    assert.equal(record.release.demoUrl, ''); assert.equal(record.release.demoAvailability, 'Source only; no hosted demo');
    const expected = Object.fromEntries(Object.entries(project).filter(([key, value]) => !['folder', 'sourceFiles'].includes(key) && value !== null));
    expected.productId = project.slug;
    const audit = record.packageAudit;
    expected.sourceBinding = project.mode === 'FREE' ? audit : project.mode === 'PAID' ? {
      ...Object.fromEntries(Object.entries(audit).filter(([key]) => key !== 'packageId')),
      templateId: `40000000-0000-4000-8000-${String(index + 1).padStart(12, '0')}`,
      versionId: `30000000-0000-4000-8000-${String(index + 1).padStart(12, '0')}`, version: '1.0.0',
    } : null;
    assert.deepEqual(snapshot, expected, 'Source and generated snapshot metadata disagree');
    assert.equal(record.release.sourceHash, hash(JSON.stringify(canonical(snapshot))), 'Immutable release snapshot hash mismatch');
    assert.match(audit.sha256, /^[0-9a-f]{64}$/); assert(audit.sizeBytes > 0 && audit.sizeBytes <= 5 * 1024 * 1024);
    for (const key of evidenceKeys[project.category] || []) assert(typeof project.categoryEvidence[key] === 'string' && project.categoryEvidence[key].trim().length >= 10, `Missing category evidence ${key}`);
    for (const key of ['includedAssets', 'excludedAssets', 'setupSteps', 'prerequisites', 'limitations']) assert(Array.isArray(project.package[key]) && project.package[key].length > 0 && project.package[key].every(value => typeof value === 'string' && value.length >= 3), `Missing package evidence ${key}`);
    assert.match(project.package.licenseTerms, /MIT/); assert(project.package.supportTerms.length >= 20);
    assert.equal(record.release.academic.shareAcademicDetails, false);
    assert.deepEqual(record.release.componentLinks, [], 'Bundled component links need independently reviewed rights');
    if (project.mode === 'PAID') { assert(Number.isSafeInteger(project.priceMinor) && project.priceMinor >= 100); assert.equal(project.currency, 'INR'); }
    else { assert.equal(project.priceMinor, null); assert.equal(project.currency, null); }
  }
  // Existing illustrative commerce bindings must keep their historical identifiers.
  assert.deepEqual(projects.slice(0, 4).map(item => [item.slug, item.mode]), [['stockroom', 'FREE'], ['booklane', 'FREE'], ['tableflow', 'PAID'], ['fieldnote', 'SHOWCASE']]);
}

export function validateProjectBytes(project, record, files, archive) {
  const names = Object.keys(files).sort(), audit = record.packageAudit;
  assert.deepEqual(audit.files, names, 'Package file manifest drift'); assert.equal(audit.entryCount, names.length);
  assert.equal(audit.sizeBytes, archive.length); assert.equal(audit.sha256, hash(archive), 'Package SHA-256 mismatch');
  for (const name of ['LICENSE', 'NOTICE', 'README.md']) assert(files[name]?.length > 100, `Missing original rights/setup ${name}`);
  assert.match(files.LICENSE.toString(), /MIT License/); assert.match(files.LICENSE.toString(), /Permission is hereby granted/);
  assert.match(files.NOTICE.toString(), /Original.*getLancer|Original getLancer/); assert.match(files.NOTICE.toString(), /synthetic|Task records/);
  assert(project.language === 'JavaScript' ? names.includes('test.mjs') && names.includes('package.json') : names.includes('test.py') && names.includes('pyproject.toml'));
  if (project.sourceFiles) assert.deepEqual([...project.sourceFiles].sort(), names, 'Reviewed source file inventory drift');
  if (['DATA_ANALYTICS', 'AI_ML'].includes(project.category)) {
    const evidence = project.dataAiEvidence, data = project.category === 'AI_ML' ? 'synthetic.json' : 'synthetic.csv';
    assert.equal(evidence.dataSha256, hash(files[data]), 'Dataset SHA-256 mismatch');
    for (const key of ['codeLicense', 'dataLicense', 'dataProvenance', 'outputSchema', 'limitations']) assert(typeof evidence[key] === 'string' && evidence[key].length >= 10);
    for (const key of ['redistributionAllowed', 'syntheticData', 'noRemoteCode']) assert.equal(evidence[key], true);
    if (project.sourceFiles) assert.match(files.DATA_LICENSE?.toString() || '', /MIT License/);
    if (project.category === 'AI_ML') {
      assert.equal(evidence.modelSha256, hash(files['model.json']), 'Model SHA-256 mismatch');
      assert.equal(evidence.modelFormat, 'JSON'); assert.match(files.MODEL_LICENSE?.toString() || '', /MIT License/);
      assert(evidence.modelProvenance.length > 20 && evidence.evaluationSplit.length > 20 && evidence.evaluationProtocol.length > 20);
      assert.notDeepEqual(JSON.parse(files['evaluation.json']), JSON.parse(files['synthetic.json']), 'Evaluation data is not separate');
    }
  }
  return Object.fromEntries(names.map(name => [name, hash(files[name])]));
}

export function verifyContent(root = repository, { reproduce = true } = {}) {
  const json = path => JSON.parse(readFileSync(join(root, path), 'utf8'));
  const components = json('backend/src/main/resources/catalog/components.json');
  const metadata = json('backend/src/main/resources/catalog/components.metadata.json');
  assert.equal(components.length, 24); assert.equal(new Set(components.map(item => item.slug)).size, 24);
  assert.deepEqual(metadata, components.map(({ files, ...item }) => item), 'Frontend/backend component metadata drift');
  for (const category of ['NAVBAR', 'SIDEBAR', 'FORM', 'CARD', 'AUTH', 'DASHBOARD']) assert.equal(components.filter(item => item.category === category).length, 4);
  for (const item of components) {
    assert.equal(item.license, 'MIT'); assert.equal(item.kind, 'FRONTEND'); assert.equal(item.executionMode, 'Browser local');
    assert.equal(item.sha256, hash(item.files['index.html'])); assert.match(item.files.LICENSE, /MIT License/);
    assert.match(item.files['README.md'], /No packages, installation, API keys or external assets/);
  }
  const labs = json('labs/catalogue.json'), languages = Object.entries(labs.languages), patterns = Object.keys(labs.patterns);
  assert.equal(languages.length, 6); assert.deepEqual(patterns.sort(), ['cache', 'payment', 'security']);
  const labInventory = [];
  assert.match(readFileSync(join(root, 'docs/v48/License.md'), 'utf8'), /MIT License/);
  assert.match(readFileSync(join(root, 'labs/README.md'), 'utf8'), /BSD-3-Clause/);
  for (const [language, definition] of languages) {
    assert(definition.primary.startsWith(`labs/${language}/`) && !definition.primary.includes('..'));
    const source = readFileSync(join(root, definition.primary)), sourceHash = hash(source);
    assert(source.length > 200 && definition.command && definition.requires);
    if (['go', 'dotnet', 'php'].includes(language)) assert.match(readFileSync(join(root, `labs/${language}/LICENSE`), 'utf8'), /MIT License/);
    for (const pattern of patterns) {
      const id = `${language}-${pattern}`;
      parseScenarioRecording(readFileSync(join(root, `labs/recordings/${id}.json`), 'utf8'), { id, language, pattern, sourceHash });
      labInventory.push({ id, sourceHash, sourceMode: 'Local native source', displayedMode: 'Recorded replay; not hosted live' });
    }
  }
  assert.equal(labInventory.length, 18);
  const projects = json('education/projects.json'), records = json('backend/src/main/resources/catalog/education.json');
  validateProjectMetadata(projects, records);
  const projectInventory = projects.map((project, index) => {
    const directory = join(root, 'education/seeds', project.folder); assert(!lstatSync(directory).isSymbolicLink());
    const files = Object.fromEntries(readdirSync(directory).filter(name => lstatSync(join(directory, name)).isFile()).sort().map(name => {
      assert(/^[A-Za-z0-9_-][A-Za-z0-9_.-]*$/.test(name)); assert(!lstatSync(join(directory, name)).isSymbolicLink());
      const bytes = readFileSync(join(directory, name)); assert(bytes.length <= 131072); return [name, bytes];
    }));
    const fileSha256 = validateProjectBytes(project, records[index], files, readFileSync(join(root, 'public/education', project.folder + '.zip')));
    return { slug: project.slug, category: project.category, mode: project.mode, demoMode: project.demoMode, packageSha256: records[index].packageAudit.sha256, fileSha256 };
  });
  execFileSync('python3', [join(root, 'scripts/build-education-material.py'), '--verify'], { cwd: root, encoding: 'utf8', timeout: 15000 });
  if (reproduce) {
    // Independent stdlib ZIP extraction executes only the reviewed exact local package bytes.
    execFileSync('python3', ['-c', `import json,os,pathlib,subprocess,tempfile,zipfile,sys
root=pathlib.Path(sys.argv[1]); projects=json.loads((root/'education/projects.json').read_text())
for project in projects:
 with tempfile.TemporaryDirectory(prefix='getlancer-v52-source-') as directory:
  with zipfile.ZipFile(root/'public/education'/(project['folder']+'.zip')) as archive:
   assert archive.testzip() is None
   for name in archive.namelist():
    assert '/' not in name and '\\\\' not in name and not name.startswith('.')
    assert archive.read(name)==(root/'education/seeds'/project['folder']/name).read_bytes()
   archive.extractall(directory)
  command=[sys.argv[2],'test.mjs'] if project['language']=='JavaScript' else [sys.executable,'test.py']
  result=subprocess.run(command,cwd=directory,env={**os.environ,'PYTHONDONTWRITEBYTECODE':'1'},capture_output=True,text=True,timeout=15)
  assert result.returncode==0,project['slug']+': '+result.stderr
  assert 'regression passed' in result.stdout.lower(),project['slug']+': missing positive evidence'
print('Clean extracted source regressions:12/12 passed')`, root, process.execPath], { cwd: root, encoding: 'utf8', timeout: 180000 });
  }
  return { counts: { components: components.length, coreLabs: labInventory.length, projects: projectInventory.length }, projectCategories: Object.fromEntries(categories.map(category => [category, projects.filter(item => item.category === category).length])), projectsReproduced: reproduce ? 12 : 0, coreLabs: labInventory, projects: projectInventory,
    limitations: ['Core-lab inventory/recorded source is verified here; actual native execution with Redis is a separate CI gate.', 'Local project reproduction does not certify hosted runtime, live payments, production capacity or real-world AI accuracy.'] };
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  assert(process.argv.slice(2).every(argument => argument === '--verify'), 'Only --verify is supported');
  console.log(JSON.stringify(verifyContent(), null, 2));
}
