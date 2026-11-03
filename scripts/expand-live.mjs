import fs from 'node:fs';
import path from 'node:path';

// Inputs are country playlists from https://iptv-org.github.io/iptv/countries/.
const input = process.argv[2];
if (!input) throw new Error('Provide the directory containing cn/hk/mo/tw/jp/us.m3u');
const regions = {cn:'地方台',hk:'香港',mo:'澳门',tw:'台湾',jp:'日本',us:'美国'};
const caps = {cn:240,hk:50,mo:30,tw:60,jp:50,us:70};
const batches = [];
for (const [country, region] of Object.entries(regions)) {
  const raw = fs.readFileSync(path.join(input,country+'.m3u'),'utf8');
  let count = 0;
  for (const block of raw.split(/(?=^#EXTINF:)/m)) {
    const [header,...rest] = block.trim().split(/\r?\n/);
    if (!header.startsWith('#EXTINF:')) continue;
    const url = rest.find(line=>/^https?:\/\//i.test(line));
    const comma = header.search(/,(?=(?:[^"]*"[^"]*")*[^"]*$)/);
    if (!url || comma < 0 || (country!=='cn' && /\[Geo-blocked\]/i.test(header))) continue;
    const name = header.slice(comma+1).trim();
    const id = header.match(/tvg-id="([^"]*)"/)?.[1] || '';
    if (!name || (country==='us' && !/group-title="(?:General|News|Sports|Entertainment|Movies)/.test(header))) continue;
    let category = region;
    if (/Phoenix(?:Chinese|InfoNews)|凤凰/i.test(name+' '+id)) category='凤凰';
    else if (/^CCTV|^CGTN/i.test(name)) category='央视';
    else if (country==='cn' && /卫视|Satellite\s*TV|^(Anhui|Beijing|Chongqing|Guangdong|Guizhou|Hebei|Heilongjiang|Henan|Hubei|Hunan|Jiangsu|Jiangxi|Jilin|Liaoning|Shandong|Shanghai|Shanxi|Sichuan|Tianjin|Yunnan|Zhejiang)\s+TV\b/i.test(name)) category='卫视';
    else if (country==='cn' && /Hunan|Changsha|Zhuzhou|Xiangtan|Yueyang|Hengyang|Changde|Yiyang|Loudi|Shaoyang|Huaihua|Zhangjiajie|Yongzhou|Chenzhou|湖南|长沙/i.test(name+' '+id)) category='湖南地方台';
    const attrs = header.slice(0,comma).replace(/\s+group-title="[^"]*"/,'');
    const m3u = attrs+' group-title="'+category+'",'+name+'\n'+rest.join('\n');
    batches.push({name,category,url,logo:header.match(/tvg-logo="([^"]*)"/)?.[1] || '',quality:name.match(/\((\d+)p\)/)?.[1] || '',m3u});
    if (++count >= caps[country]) break;
  }
}
for (const target of ['desktop/assets/live.m3u','android-legacy/assets/live-global.m3u','android/resources/app/src/main/assets/aobing/live-global.m3u']) {
  const original = fs.readFileSync(target,'utf8').trimEnd();
  const seen = new Set(original.split(/\r?\n/).filter(line=>/^https?:/.test(line)));
  const added = batches.filter(row=>!seen.has(row.url)&&seen.add(row.url));
  fs.writeFileSync(target,original+'\n'+added.map(row=>row.m3u).join('\n')+(added.length?'\n':''));
}
const target='desktop/assets/live-channels.json';
const existing=JSON.parse(fs.readFileSync(target,'utf8'));
const seen=new Set(existing.map(row=>row.url));
const added=batches.filter(row=>!seen.has(row.url)&&seen.add(row.url)).map(({m3u,...row})=>row);
fs.writeFileSync(target,JSON.stringify([...existing,...added],null,2)+'\n');
console.log('New candidates:',added.length,Object.fromEntries([...new Set(added.map(x=>x.category))].map(g=>[g,added.filter(x=>x.category===g).length])));
