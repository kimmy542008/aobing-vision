// Public server-rendered catalog only; never evaluate site JavaScript or decrypt media.
const origin='https://hongguoduanju.com';
function routerData(html){
 const marker='_ROUTER_DATA = ',at=html.indexOf(marker);if(at<0)throw Error('官网目录格式已变化');
 let start=at+marker.length,depth=0,quoted=false,escaped=false;
 for(let i=start;i<html.length;i++){
  const c=html[i];if(quoted){if(escaped)escaped=false;else if(c==='\\')escaped=true;else if(c==='"')quoted=false;continue;}
  if(c==='"')quoted=true;else if(c==='{')depth++;else if(c==='}'&&--depth===0)return JSON.parse(html.slice(start,i+1));
 }throw Error('官网目录数据不完整');
}
function extract(html){
 const pages=Object.values(routerData(html).loaderData||{});
 const page=pages.find(p=>Array.isArray(p?.recommendList));if(!page)throw Error('官网暂未提供目录');
 return {items:page.recommendList.filter(v=>/^\d+$/.test(v.series_id)).map(v=>({id:v.series_id,name:v.series_name,poster:v.series_cover,total:v.episode_cnt,accessible:v.accessible_episode_cnt})),pages:Math.min(500,Math.max(1,Number(page.pagination?.totalPages)||1))};
}
async function catalog(page){
 if(!Number.isInteger(page)||page<1||page>500)throw Error('页码无效');
 const response=await fetch(origin+'/category/comic-drama?page='+page,{signal:AbortSignal.timeout(20000),headers:{'User-Agent':'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/142.0.0.0 Safari/537.36'}});
 if(!response.ok)throw Error('红果官网返回 HTTP '+response.status);
 const html=await response.text();if(html.length>8*1024*1024)throw Error('目录响应过大');return extract(html);
}
async function search(query,page=1){
 if(typeof query!=='string'||!query.trim()||Array.from(query.trim()).length>30)throw Error('红果搜索请输入1–30个字符');
 if(!Number.isInteger(page)||page<1||page>500)throw Error('页码无效');
 const params=new URLSearchParams({query:query.trim(),offset:String((page-1)*20),count:'20'});
 const response=await fetch(origin+'/api/search/result?'+params,{signal:AbortSignal.timeout(20000),headers:{'User-Agent':'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/142.0.0.0 Safari/537.36','Referer':origin+'/'}});
 if(!response.ok)throw Error('红果搜索返回 HTTP '+response.status);
 const data=await response.json();if(!Array.isArray(data.searchList))throw Error('红果搜索数据格式已变化');
 return {items:data.searchList.filter(v=>/^\d+$/.test(v.video_data?.series_id)).map(v=>({id:v.video_data.series_id,name:v.video_data.series_title||v.name,poster:v.video_data.series_cover,total:v.video_data.episode_cnt,accessible:data.accessibleEpisodeCnt})),pages:Math.min(500,Math.max(1,Number(data.totalPages)||1))};
}
module.exports={catalog,search,extract,origin};
