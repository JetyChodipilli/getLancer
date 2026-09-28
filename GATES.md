# Gates: V1.5 follow-up
OWNS: backend/src/main/java/com/getlancer/DemoHealth.java, backend/src/main/java/com/getlancer/TrustController.java, backend/src/test/java/com/getlancer/TrustTest.java, app/components/trust-workspace.tsx, docs/V1_5_IMPLEMENTATION.md, scripts/smoke-docker.mjs, GATES.md
Scope: close the two identified V1.5 code gaps and verify CI without claiming production services are connected.

- [x] G1: DNS timeout, saturation and successful resolution are covered by backend tests.
  CHECK: JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 /workspace/scratch/5e1e0480d9d7/apache-maven-3.9.11/bin/mvn -o -Dmaven.repo.local=/workspace/scratch/5e1e0480d9d7/maven-cache -B -f backend/pom.xml -Dtest=TrustTest test
  EXPECT: BUILD SUCCESS
  EVIDENCE: automatic-evidence=v1; definition-sha256=58f7e1311bd8019a256a1e311f7e7a262cb2966992e78e7b5a7c22265575aca2; exit=0; EXPECT=matched; output-sha256=8d942c8c9d3d16c7c5a51d47cd6b4e365d52fd3a30a033a7f1e1358b229b455f; output-bytes=2435; shell=/bin/sh; cwd=/workspace/sites/getlancer; path=6cbec16fe8d0/13 entries
- [x] G2: Frontend type checking succeeds after adding revocation controls.
  CHECK: npx tsc --noEmit && node -e "console.log('TYPECHECK_COMPLETE')"
  EXPECT: TYPECHECK_COMPLETE
  EVIDENCE: automatic-evidence=v1; definition-sha256=0e4f122b58c6a386b10f565f641e210b8928f6f2a481a0bb59db706b263fd76a; exit=0; EXPECT=matched; output-sha256=65e99d73e5e1e0b5856b1d44f518de639d94647c6ca46979c302a789bda8dd29; output-bytes=119; shell=/bin/sh; cwd=/workspace/sites/getlancer; path=6cbec16fe8d0/13 entries
- [x] G3: The exact pushed commit passes backend, frontend and Docker validation in GitHub.
  EVIDENCE: Implementation commit 9e201462d7706e4c51e7a831eced493f8cbf7517 passed GitHub run 36379565906: backend job 108792322635, frontend 108792322813, docker-startup 108792322840 all completed successfully. Backend: 117 tests; frontend: 27 tests. Docker includes connected journeys, database recovery and encrypted restore. The subsequent ledger-only commit changes no implementation or test input.
- [x] G4: The administrator can discover verified evidence and request revocation with a reason; the deployment limits remain explicit.
  EVIDENCE: Reviewed protected admin queue, verified-only revocation guard, required 10–2000 character reason, audit event, UI reload, demo simulation and documented hosted-service activation limits. Docker smoke also asserts the verified queue and repeated-revocation rejection.
