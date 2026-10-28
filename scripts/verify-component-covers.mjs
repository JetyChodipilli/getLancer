import {readFileSync} from 'node:fs';
import assert from 'node:assert/strict';
import {createHash} from 'node:crypto';
const recipes=JSON.parse(readFileSync('backend/src/main/resources/catalog/components.json','utf8'));
const covers=JSON.parse(readFileSync('public/component-covers/index.json','utf8'));
const hash=bytes=>createHash('sha256').update(bytes).digest('hex');
assert.deepEqual(Object.keys(covers).sort(),recipes.map(recipe=>recipe.slug).sort());
for(const recipe of recipes){
 const cover=covers[recipe.slug];
 assert.equal(cover.file,recipe.slug+'.jpg');
 assert.equal(cover.sourceSha256,hash(recipe.files['index.html']));
 assert.equal(cover.sourceSha256,recipe.sha256);
 const image=readFileSync('public/component-covers/'+cover.file);
 assert.equal(image.readUInt16BE(0),0xffd8);assert(image.length>1024);
 assert.equal(hash(image),cover.imageSha256);
}
console.log('COMPONENT_COVERS_VERIFIED '+recipes.length+' original source and image digests');
