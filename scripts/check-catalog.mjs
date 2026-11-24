// Rios敖丙: compare shared content, allowing presentation-only M3U logo attributes.
import fs from 'node:fs';
import assert from 'node:assert/strict';
const clean=s=>s.replace(/ tvg-logo="[^"]*"/g,'').replace(/\r/g,'').trim();
for(const name of ['live-snapshot.m3u','live-global.m3u','live-candidates.m3u','vod.json']){
 const normalize=s=>name==='vod.json'?JSON.stringify(JSON.parse(s).sites):clean(s);
 const canonical=normalize(fs.readFileSync('shared/catalog/'+name,'utf8'));
 for(const dir of ['android-legacy/assets/','android/resources/app/src/main/assets/aobing/']){
  assert.equal(normalize(fs.readFileSync(dir+name,'utf8')),canonical,dir+name+' differs from shared catalog');
 }
}
console.log('TV, Huawei-compatible APK, mobile and API19 embedded catalogs agree.');
