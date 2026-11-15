import json
import pathlib
import re

root = pathlib.Path(__file__).resolve().parents[1]
legacy = root/'android-legacy/assets'
android = root/'android/resources/app/src/main/assets/aobing'
for name in ('snapshot', 'global', 'candidates'):
    (android/f'live-{name}.m3u').write_bytes((legacy/f'live-{name}.m3u').read_bytes())
rows = json.loads((root/'desktop/assets/live-channels.json').read_text(encoding='utf-8'))
rows = [row for row in rows if not (
    re.search(r'^(CCTV|CGTN)', row['name'], re.I) or row.get('category') == '央视')]
official = (legacy/'live-snapshot.m3u').read_text(encoding='utf-8')
for block in re.split(r'(?=^#EXTINF:)', official, flags=re.M):
    if not block.startswith('#EXTINF:'):
        continue
    header, *lines = block.strip().splitlines()
    url = next(line for line in lines if line.startswith('https://'))
    rows.insert(0, dict(name=header.split(',', 1)[1], category='央视新闻素材', url=url))
(root/'desktop/assets/live-channels.json').write_text(json.dumps(rows,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
playlist = '#EXTM3U\n' + ''.join(
    f'#EXTINF:-1 group-title="{row["category"]}",{row["name"]}\n{row["url"]}\n' for row in rows)
(root/'desktop/assets/live.m3u').write_text(playlist,encoding='utf-8')
clean_url = 'https://raw.githubusercontent.com/kimmy542008/aobing-vision/main/android/resources/app/src/main/assets/aobing/live-global.m3u'
for path in (android/'live-config.json', android/'vod.json'):
    config = json.loads(path.read_text(encoding='utf-8-sig'))
    for live in config['lives']:
        if live['url'].endswith('countries/cn.m3u'):
            live['url'] = clean_url
            live['name'] = '在线已清理频道清单'
        if 'live-snapshot.m3u' in live['url']:
            live['name'] = 'CCTV+ 官方新闻素材'
    path.write_text(json.dumps(config,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
print('Desktop clean candidates:',len(rows))
