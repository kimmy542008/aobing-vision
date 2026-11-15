import pathlib
import re
import sys

assets = pathlib.Path('android-legacy/assets')
current = pathlib.Path(sys.argv[1]).read_text(encoding='utf-8')
def blocks(text):
    return [b.strip() for b in re.split(r'(?=^#EXTINF:)',text,flags=re.M) if b.startswith('#EXTINF')]
official = [re.sub(r'group-title="[^"]*"', 'group-title="央视新闻素材"', b)
            for b in blocks(current) if 'news.cctvplus.com/' in b]
if not official:
    raise RuntimeError('No publisher-hosted CCTV+ feeds found')
for playlist in assets.glob('live-*.m3u'):
    kept = [b for b in blocks(playlist.read_text(encoding='utf-8'))
            if not re.search(r'tvg-id="(?:CCTV|CGTN)|group-title="央视"|,CCTV|,CGTN', b)]
    playlist.write_text('#EXTM3U\n'+'\n'.join(kept)+'\n',encoding='utf-8')
(assets/'live-snapshot.m3u').write_text('#EXTM3U\n'+'\n'.join(official)+'\n',encoding='utf-8')
print('Official news feeds',len(official))
