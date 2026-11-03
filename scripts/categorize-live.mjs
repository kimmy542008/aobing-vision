import fs from 'node:fs';

const order = ['央视','卫视','海外','娱乐','体育','NBA','新闻','电影','卡通动漫','音乐','文化','生活健康','游戏直播','地方台','其他'];
const mapping = {
  '国际':'海外', 'Entertainment':'娱乐', 'Sports':'体育', 'News':'新闻',
  'Movies':'电影', 'Music':'音乐', 'Culture':'文化', 'Documentary':'文化',
  'Education':'文化', 'Lifestyle':'生活健康', 'Weather':'新闻', 'Business':'新闻',
  'Animation':'卡通动漫', 'Kids':'卡通动漫', '国内':'地方台',
};
function category(name, group, id='') {
  if (/\bNBA\b/i.test(name)) return 'NBA';
  if (/^CCTV[-\s\d]|^CGTN\b|^央视/i.test(name)) return '央视';
  if (/卫视|Satellite\s*TV|^(Anhui|Hunan|Hubei|Jiangsu|Jiangxi|Liaoning|Shandong|Sichuan|Tianjin|Zhejiang)\s+TV\b/i.test(name)) return '卫视';
  if (order.includes(group)) return group;
  return mapping[group.split(';')[0]] || (/\.cn(?:@|$)/i.test(id) ? '地方台' : '其他');
}
const path = 'desktop/assets/live-channels.json';
const rows = JSON.parse(fs.readFileSync(path,'utf8'));
for (const row of rows) row.category = category(row.name,row.category || row.group || '',row.id || '');
fs.writeFileSync(path, JSON.stringify(rows,null,2)+'\n');
for (const path of [
  'desktop/assets/live.m3u',
  ...['android-legacy/assets','android/resources/app/src/main/assets/aobing'].flatMap(
    dir => ['snapshot','global','candidates'].map(name => `${dir}/live-${name}.m3u`)),
]) {
  const original = fs.readFileSync(path,'utf8');
  const result = original.replace(/^#EXTINF:.*$/gm, line => {
    const comma = line.search(/,(?=(?:[^"]*"[^"]*")*[^"]*$)/);
    if (comma < 0) return line;
    const attributes = line.slice(0,comma);
    const existing = attributes.match(/group-title="([^"]*)"/);
    const group = category(line.slice(comma+1).trim(),existing?.[1] || '',attributes.match(/tvg-id="([^"]*)"/)?.[1]);
    return (existing ? attributes.replace(existing[0],`group-title="${group}"`) :
      attributes+` group-title="${group}"`) + line.slice(comma);
  });
  fs.writeFileSync(path,result);
}
console.log(Object.fromEntries(order.map(group=>[group,rows.filter(row=>row.category===group).length])));
