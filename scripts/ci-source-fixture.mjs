// Test data only: stdlib ZIP generation, never a runtime package or seller seed.
import {execFileSync} from 'node:child_process';
export function sourceFixture(){return execFileSync('python3',['-c',String.raw`
import io,sys,zipfile
stream=io.BytesIO()
with zipfile.ZipFile(stream,'w',zipfile.ZIP_DEFLATED) as archive:
 archive.writestr('README.md','CI source fixture. Build using npm. Never execute an untrusted seller package.')
 archive.writestr('LICENSE.txt','Synthetic CI source license. One commercial end product; no source redistribution. No third-party dependencies included.')
 archive.writestr('package.json','{"name":"ci-source-fixture","private":true,"version":"1.0.0"}')
 archive.writestr('src/index.js','export const fixture = "test-only";\n')
sys.stdout.buffer.write(stream.getvalue())
`]);}
