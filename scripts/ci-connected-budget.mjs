// Isolate counters between journeys, only inside the verified disposable CI stack.
import assert from 'node:assert/strict';
import {execFileSync} from 'node:child_process';

export function resetCiRateBudgets(fixture,workers,run=execFileSync){
  const project=process.env.COMPOSE_PROJECT_NAME;
  assert.equal(process.env.CI,'true','Connected counter isolation requires disposable CI.');
  assert.match(project||'',/^getlancer-ci-[0-9]+$/,'Use an isolated CI Compose project.');
  assert.equal(fixture.project,project,'The fixture must belong to this CI stack.');
  assert.equal(workers,1,'Counter isolation requires sequential connected journeys.');
  const options={encoding:'utf8',timeout:15000,stdio:['ignore','pipe','pipe']};
  const command=args=>run('docker',args,options).trim();
  const id=command(['compose','ps','-q','db']);
  assert.match(id,/^[a-f0-9]{64}$/,'Require one concrete database container.');
  const [container]=JSON.parse(command(['inspect',id]));
  assert.equal(container.Config.Labels['com.docker.compose.project'],project);
  assert.equal(container.Config.Labels['com.docker.compose.service'],'db');
  const mounts=container.Mounts.filter(mount=>mount.Destination==='/var/lib/postgresql/data');
  assert.equal(mounts.length,1,'Require one dedicated database volume.');
  const [volume]=mounts;
  assert.equal(volume.Type,'volume','Refuse host database mounts.');
  assert.equal(volume.Name,project+'_docker-database','Refuse a shared database volume.');
  const [metadata]=JSON.parse(command(['volume','inspect',volume.Name]));
  assert.equal(metadata.Labels['com.docker.compose.project'],project);
  const query=sql=>command(['exec',id,'psql','-U','postgres','-d','getLancer','-X','-At','--set','ON_ERROR_STOP=1','-c',sql]);
  assert.equal(query("SELECT current_database()='getLancer' AND EXISTS (SELECT 1 FROM getlancer.users WHERE email='ci-admin@example.test') AND NOT EXISTS (SELECT 1 FROM getlancer.users WHERE email !~ '^ci-[^@]+@example[.]test$')"),'t','Refuse a database containing non-fixture accounts.');
  // No reset occurs during a journey: all actual rate limits remain active in that test.
  query('DELETE FROM getlancer.rate_buckets');
}
