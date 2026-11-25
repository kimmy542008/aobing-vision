let hongguoPage=1,hongguoPages=1,hongguoTicket=0;
async function loadHongguo(page){
 const ticket=++hongguoTicket;$('#hongguo-status').textContent='正在读取红果官网漫剧目录…';
 try{
  const result=await call('hongguo-catalog',page);if(ticket!==hongguoTicket)return;
  hongguoPage=page;hongguoPages=result.pages;const grid=$('#hongguo-grid');grid.replaceChildren();
  for(const item of result.items){
   const card=document.createElement('button');card.className='card';
   const image=document.createElement('img');if(/^https:\/\//.test(item.poster||''))image.src=item.poster;image.alt='';image.loading='lazy';
   const copy=document.createElement('div'),title=document.createElement('strong'),detail=document.createElement('small');
   title.textContent=item.name;detail.textContent='全'+item.total+'集 · 网页可访问'+(item.accessible??'以官网为准')+'集';copy.append(title,detail);card.append(image,copy);
   card.onclick=async()=>{try{await call('hongguo-play',item.id);}catch(e){$('#hongguo-status').textContent=e.message;}};grid.append(card);
  }
  $('#hongguo-status').textContent='红果官网目录 · 第 '+page+' / '+hongguoPages+' 页 · 播放范围及登录要求由官网决定';
  $('#hongguo-prev').disabled=page<=1;$('#hongguo-next').disabled=page>=hongguoPages;
 }catch(e){if(ticket===hongguoTicket)$('#hongguo-status').textContent='读取失败：'+e.message;}
}
$('#hongguo').onclick=()=>{$('#hongguo-library').showModal();loadHongguo(hongguoPage);};
$('#hongguo-close').onclick=()=>{$('#hongguo-library').close();++hongguoTicket;};
$('#hongguo-prev').onclick=()=>loadHongguo(Math.max(1,hongguoPage-1));
$('#hongguo-next').onclick=()=>loadHongguo(Math.min(hongguoPages,hongguoPage+1));
