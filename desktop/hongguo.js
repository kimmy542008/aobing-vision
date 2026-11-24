// This is explicitly an installed official-client entry, not a playback API.
$('#hongguo').onclick=async()=>{
  try{await call('hongguo');status('已打开红果官方客户端；敖丙视界内播接口尚未接入');}
  catch(e){status(e.message);}
};
