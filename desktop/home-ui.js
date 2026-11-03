const sections = {
  movie: ['电影', /电影|动作片|喜剧片|爱情片|科幻片|恐怖片|剧情片|战争片/],
  series: ['剧集', /剧集|连续剧|电视剧|剧$/],
  documentary: ['纪录片', /纪录|纪实/],
  anime: ['动漫', /动漫|动画|番剧/],
  shorts: ['短剧', /短剧|微短剧/],
  comic: ['漫剧', /漫剧|动态漫画/],
  other: ['其它', /./],
};
const sectionKeys = ['comic','shorts','documentary','anime','movie','series'];
function sectionOf(name) {
  return sectionKeys.find(key => sections[key][1].test(name || '')) || 'other';
}
const hero = $('#ice-hero');
function preview(name, artwork, label, action, caption) {
  $('#hero-title').textContent = name;
  $('#hero-label').textContent = label;
  const image = $('#hero-art');
  image.hidden = true;
  image.removeAttribute('src');
  if (/^https?:\/\//i.test(artwork || '')) {
    image.onload = () => { image.hidden = false; };
    image.onerror = () => { image.hidden = true; };
    image.src = artwork;
  }
  $('#hero-browse').textContent = caption;
  $('#hero-browse').onclick = action;
}
function watchPreview(card, update) {
  card.addEventListener('focus', update);
  card.addEventListener('pointerenter', update);
}
function mediaPreview(card, item) {
  const update = () => preview(item.vod_name, item.vod_pic, item.type_name || '影视预览', () => detail(item), '查看选集');
  watchPreview(card, update);
  return update;
}
function liveCard(item) {
  const playChannel = () => play(item.name,item.url);
  const card = button('',playChannel);
  card.className = 'channel visual-channel';
  const art = document.createElement('div');
  art.className = 'station-art';
  const icon = document.createElement('img');
  const fallback = document.createElement('span');
  fallback.textContent = item.name.match(/^(?:CCTV[- ]?\d+\+?|CGTN)/i)?.[0] || '▣';
  icon.alt = '';
  if (/^https?:\/\//i.test(item.logo || '')) {
    icon.loading = 'lazy';
    icon.onload = () => { fallback.hidden = true; };
    icon.onerror = () => { icon.remove(); fallback.hidden = false; };
    icon.src = item.logo;
    art.append(icon);
  }
  art.append(fallback);
  const title = document.createElement('strong');
  title.textContent = item.name;
  card.append(art,title);
  const update = () => preview(item.name,item.logo,channelGroup(item),playChannel,'观看直播');
  watchPreview(card,update);
  return {card,update};
}
const originalBrowse = load;
function categoryList(key) {
  return state.categories.filter(type => sectionOf(type.type_name) === key);
}
function shelf(title, nodes) {
  if (!nodes.length) return;
  const row = document.createElement('section');
  const heading = document.createElement('h2');
  heading.textContent = title;
  const track = document.createElement('div');
  track.className = 'shelf-track';
  track.append(...nodes);
  row.append(heading,track);
  $('#grid').append(row);
}
function mediaNodes(items) {
  const container = document.createElement('div');
  cards(items,container);
  return [...container.children];
}
load = async function () {
  hero.hidden = ['favorites','history'].includes(state.page);
  const grid = $('#grid');
  grid.classList.toggle('home-shelves',state.page === 'discover' && !state.search && !state.type);
  preview('敖丙视界','','节目预览',() => grid.scrollIntoView({block:'start'}),'浏览节目');
  if (!sections[state.page] && !grid.classList.contains('home-shelves')) return originalBrowse();
  const ticket = ++serial;
  const key = state.page;
  grid.replaceChildren();
  $('#categories').replaceChildren();
  $('#paging').hidden = key === 'discover';
  $('#heading').textContent = sections[key]?.[0] || '首页';
  status('正在加载…');
  try {
    if (key === 'discover') {
      const fetchShelf = async type => {
        const first = categoryList(type)[0];
        if (!first) return [];
        const result = await call('catalog',{ac:'detail',t:first.type_id,pg:1});
        return (result.list || []).slice(0,8);
      };
      const [films,series,live] = await Promise.allSettled([fetchShelf('movie'),fetchShelf('series'),call('live')]);
      if (ticket !== serial) return;
      shelf('电影',mediaNodes(films.value || []));
      shelf('剧集',mediaNodes(series.value || []));
      shelf('电视直播',(live.value || []).slice(0,8).map(item => liveCard(item).card));
      if (grid.querySelector('.card,.channel')) grid.querySelector('.card,.channel').dispatchEvent(new Event('focus'));
      status(grid.children.length ? '' : '暂无可展示的节目，请检查源配置');
      return;
    }
    const types = categoryList(key);
    if (!types.length) {
      $('#paging').hidden = true;
      status('当前片源没有提供「'+sections[key][0]+'」分类');
      return;
    }
    if (!types.some(type => String(type.type_id) === state.type)) state.type = String(types[0].type_id);
    for (const type of types) {
      const id = String(type.type_id);
      const choice = button(type.type_name,() => { state.type = id; state.pg = 1; load(); });
      choice.classList.toggle('active',id === state.type);
      $('#categories').append(choice);
    }
    const data = await call('catalog',{ac:'detail',t:state.type,pg:state.pg});
    if (ticket !== serial) return;
    cards(data.list || []);
    state.pages = Number(data.pagecount) || 1;
    $('#page').textContent = state.pg+' / '+state.pages;
    $('#prev').disabled = state.pg <= 1;
    $('#next').disabled = state.pg >= state.pages;
    status(grid.children.length ? '' : '此分类暂无节目');
  } catch (error) {
    if (ticket === serial) status('加载失败：'+error.message);
  }
};
(async()=>{try{init=await call('init');await refresh()}catch(e){status(e.message)}})();
