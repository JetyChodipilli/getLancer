import {test} from 'node:test';
import assert from 'node:assert/strict';
import {safeExternalUrl} from '../lib/safe-url.ts';
test('public evidence URLs accept HTTPS and reject executable or credential-bearing links',()=>{
 assert.equal(safeExternalUrl('https://example.com/project?view=public'),'https://example.com/project?view=public');
 for(const url of ['javascript:alert(1)','data:text/html,<script>alert(1)</script>','http://example.com','//example.com','https://user:password@example.com','https://example.com/\nattack','https://example.com/ has space',null,{},'https://example.com/'+ 'x'.repeat(2048)])assert.equal(safeExternalUrl(url),undefined);
});
