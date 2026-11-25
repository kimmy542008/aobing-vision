let hongguoPage=1,hongguoPages=1,hongguoTicket=0,hongguoQuery='';
async function loadHongguo(page){
 const ticket=++hongguoTicket;$('#hongguo-status').textContent='正在读取红果官网漫剧目录…';
 try{
  const result=await (hongguoQuery?call('hongguo-search',{query:hongguoQuery,page}):call('hongguo-catalog',page));if(ticket!==hongguoTicket)return;
  hongguoPage=page;hongguoPages=result.pages;const grid=$('#hongguo-grid');grid.replaceChildren();
  for(const item of result.items){
   const card=document.createElement('button');card.className='card';
   const image=document.createElement('img');if(/^https:\/\//.test(item.poster||''))image.src=item.poster;image.alt='';image.loading='lazy';
   const copy=document.createElement('div'),title=document.createElement('strong'),detail=document.createElement('small');
   title.textContent=item.name;detail.textContent='全'+item.total+'集 · 网页可访问'+(item.accessible??'以官网为准')+'集';copy.append(title,detail);card.append(image,copy);
   card.onclick=async()=>{try{await call('hongguo-play',item.id);}catch(e){$('#hongguo-status').textContent=e.message;}};grid.append(card);
  }
  $('#hongguo-status').textContent=(hongguoQuery?'红果搜索“'+hongguoQuery+'”':'红果官网目录')+' · 第 '+page+' / '+hongguoPages+' 页 · 播放范围及登录要求由官网决定';
  $('#hongguo-prev').disabled=page<=1;$('#hongguo-next').disabled=page>=hongguoPages;
 }catch(e){if(ticket===hongguoTicket)$('#hongguo-status').textContent='读取失败：'+e.message;}
}
$('#hongguo').onclick=()=>{$('#hongguo-library').showModal();loadHongguo(hongguoPage);};
$('#hongguo-close').onclick=()=>{$('#hongguo-library').close();++hongguoTicket;};
$('#hongguo-prev').onclick=()=>loadHongguo(Math.max(1,hongguoPage-1));
$('#hongguo-next').onclick=()=>loadHongguo(Math.min(hongguoPages,hongguoPage+1));
$('#hongguo-search').onsubmit=e=>{e.preventDefault();hongguoQuery=$('#hongguo-query').value.trim();loadHongguo(1);};
$('#hongguo-reset').onclick=()=>{hongguoQuery='';$('#hongguo-query').value='';loadHongguo(1);};
function hongguoSearchNodes(items){
 return items.map(item=>{
  const card=button('',async()=>{try{await call('hongguo-play',item.id);}catch(e){status(e.message);}});card.className='card';
  const image=document.createElement('img');image.alt='';image.loading='lazy';if(/^https:\/\//.test(item.poster||''))image.src=item.poster;
  const copy=document.createElement('div'),title=document.createElement('strong'),source=document.createElement('small');
  title.textContent=item.name;source.textContent='红果漫剧 · 网页可访问'+(item.accessible??'以官网为准')+'集';copy.append(title,source);card.append(image,copy);return card;
 });
}
const browseWithoutGlobalSearch=load;
load=async function(){
 if(state.page!=='discover'||!state.search)return browseWithoutGlobalSearch();
 const ticket=++serial,query=state.search,page=state.pg;
 sourceBar.hidden=true;hero.hidden=true;$('#categories').replaceChildren();$('#heading').textContent='全局搜索';
 const grid=$('#grid');grid.classList.add('home-shelves');grid.replaceChildren();$('#paging').hidden=false;
 $('#prev').disabled=true;$('#next').disabled=true;status('正在搜索“'+query+'”…');
 let pages=1,errors=0;
 const sources=[
  ['影视点播源',()=>call('catalog',{ac:'detail',wd:query,pg:page}),data=>mediaNodes(data.list||[]),data=>Number(data.pagecount)||1],
  ['红果漫剧',()=>call('hongguo-search',{query,page}),data=>hongguoSearchNodes(data.items),data=>data.pages]
 ];
 await Promise.allSettled(sources.map(async([name,fetchData,nodes,pageCount])=>{
  const section=document.createElement('section'),heading=document.createElement('h2'),track=document.createElement('div');
  heading.textContent=name;track.className='shelf-track';track.textContent='正在搜索…';section.append(heading,track);grid.append(section);
  try{const data=await fetchData();if(ticket!==serial)return;pages=Math.max(pages,pageCount(data));const cards=nodes(data);track.replaceChildren(...cards);if(!cards.length)track.textContent='本来源暂无匹配内容';}
  catch(e){if(ticket!==serial)return;errors++;track.textContent=name+'读取失败：'+e.message;}
 }));
 if(ticket!==serial)return;state.pages=pages;$('#page').textContent=page+' / '+pages;
 $('#prev').disabled=page<=1;$('#next').disabled=page>=pages;
 status('“'+query+'” · 已搜索影视点播源与红果漫剧'+(errors?' · '+errors+'个来源失败，其它结果仍可使用':''));
};
