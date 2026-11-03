package com.rios.aobing.legacy;

import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

/** Standalone API19 edition; no AndroidX, Media3, WebView or native ABI dependencies. */
public class MainActivity extends Activity {
    static final String VERSION="0.4.7";
    final ExecutorService executor=Executors.newSingleThreadExecutor();
    final ArrayList<Entry> channels=new ArrayList<Entry>();
    final ArrayList<Entry> shown=new ArrayList<Entry>();
    final ArrayList<Entry> movies=new ArrayList<Entry>();
    ArrayList<Entry> beforePlayback;
    SharedPreferences prefs;
    TextView status;
    GridView list;
    ListView playingEpisodes;
    VideoView video;
    StreamProxy proxy;
    String mode="live", group="全部", movieQuery="", currentName="", currentUrl="";
    String defaultApi="";
    int moviePage=1, moviePages=1, requestId=0, currentChannel=-1;
    boolean playing=false;
    static class Entry {
        String name,url,id,group,poster="";
        Entry(String name,String url,String id,String group){this.name=name;this.url=url;this.id=id;this.group=group;}
    }
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        prefs=getSharedPreferences("aobing-legacy",MODE_PRIVATE);
        try {
            InputStream root = getAssets().open("isrgrootx1.pem");
            try { Net.configureTrust(root); } finally { root.close(); }
        } catch (Exception e) { android.util.Log.w("AobingTLS", "Using system trust anchors", e); }
        try { defaultApi=new JSONObject(asset("vod.json")).getJSONArray("sites").getJSONObject(0).optString("api"); } catch(Exception ignored){}
        home(); loadLive();
    }
    int dp(int value){return (int)(value*getResources().getDisplayMetrics().density+0.5f);}
    TextView text(String value,int size){TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(Color.WHITE);t.setPadding(dp(12),dp(8),dp(12),dp(8));return t;}
    Button button(String label,View.OnClickListener listener){
        Button b=new Button(this);b.setText(label);b.setTextSize(16);b.setTextColor(Color.WHITE);
        b.setBackgroundDrawable(tileColors());b.setPadding(dp(14),0,dp(14),0);
        LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-2,dp(40));params.setMargins(0,dp(4),dp(8),dp(4));b.setLayoutParams(params);
        b.setOnClickListener(listener);return b;
    }
    android.graphics.drawable.StateListDrawable tileColors(){
        android.graphics.drawable.StateListDrawable states=new android.graphics.drawable.StateListDrawable();
        states.addState(new int[]{android.R.attr.state_focused},tile(0xff176d9b,0xff77ddff));
        states.addState(new int[]{android.R.attr.state_pressed},tile(0xff176d9b,0xff77ddff));
        states.addState(new int[]{},tile(0xff1c3850,0xff294d68));
        return states;
    }
    android.graphics.drawable.GradientDrawable tile(int color,int border){
        android.graphics.drawable.GradientDrawable d=new android.graphics.drawable.GradientDrawable();
        d.setColor(color);d.setCornerRadius(dp(10));d.setStroke(dp(2),border);return d;
    }
    LinearLayout column(){LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(0xff102236);return root;}
    void note(String value){if(status!=null)status.setText(value);}
    void home(){
        playing=false; video=null;
        LinearLayout root=column();
        root.setPadding(dp(16),dp(10),dp(16),dp(8));
        LinearLayout brand=new LinearLayout(this);brand.setGravity(Gravity.CENTER_VERTICAL);
        ImageView mascot=new ImageView(this);mascot.setImageResource(getResources().getIdentifier("icon","drawable",getPackageName()));
        brand.addView(mascot,new LinearLayout.LayoutParams(dp(44),dp(44)));
        brand.addView(text("敖丙视界",27));root.addView(brand);
        LinearLayout tools=new LinearLayout(this);
        tools.addView(button("直播",new View.OnClickListener(){public void onClick(View v){mode="live";loadLive();}}));
        tools.addView(button("电影/剧集",new View.OnClickListener(){public void onClick(View v){mode="movie";moviePage=1;loadMovies();}}));
        tools.addView(button("分类",new View.OnClickListener(){public void onClick(View v){groups();}}));
        tools.addView(button("搜索",new View.OnClickListener(){public void onClick(View v){search();}}));
        tools.addView(button("播放地址",new View.OnClickListener(){public void onClick(View v){direct();}}));
        tools.addView(button("配置",new View.OnClickListener(){public void onClick(View v){config();}}));
        tools.addView(button("关于",new View.OnClickListener(){public void onClick(View v){about();}}));
        HorizontalScrollView horizontal=new HorizontalScrollView(this);horizontal.addView(tools);root.addView(horizontal);
        FrameLayout hero=new FrameLayout(this);
        ImageView landscape=new ImageView(this);
        landscape.setImageResource(getResources().getIdentifier("ice_hero","drawable",getPackageName()));
        landscape.setScaleType(ImageView.ScaleType.CENTER_CROP);
        hero.addView(landscape,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout heroText=new LinearLayout(this);heroText.setOrientation(LinearLayout.VERTICAL);
        heroText.setPadding(dp(18),0,0,0);
        heroText.addView(text("电影与剧集",29));
        heroText.addView(button("浏览片单",new View.OnClickListener(){public void onClick(View v){mode="movie";moviePage=1;loadMovies();}}));
        hero.addView(heroText,new FrameLayout.LayoutParams(dp(280),-2,Gravity.LEFT|Gravity.CENTER_VERTICAL));
        root.addView(hero,new LinearLayout.LayoutParams(-1,dp(140)));
        status=text("方向键选择，确定播放；播放时菜单键查看操作",17);root.addView(status);
        list=new GridView(this);list.setNumColumns(3);list.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        list.setHorizontalSpacing(dp(10));list.setVerticalSpacing(dp(10));list.setDrawSelectorOnTop(true);
        android.graphics.drawable.GradientDrawable focus=tile(Color.TRANSPARENT,0xff77ddff);
        android.graphics.drawable.StateListDrawable selector=new android.graphics.drawable.StateListDrawable();
        selector.addState(new int[]{android.R.attr.state_focused},focus);
        selector.addState(new int[]{android.R.attr.state_pressed},tile(0x3377ddff,0xff77ddff));
        selector.addState(new int[]{},new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
        list.setSelector(selector);
        root.addView(list,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout pages=new LinearLayout(this);
        pages.addView(button("上一页",new View.OnClickListener(){public void onClick(View v){if(mode.equals("movie")&&moviePage>1){moviePage--;loadMovies();}}}));
        pages.addView(button("下一页",new View.OnClickListener(){public void onClick(View v){if(mode.equals("movie")&&moviePage<moviePages){moviePage++;loadMovies();}}}));
        root.addView(pages);setContentView(root);
        list.setOnItemClickListener(new AdapterView.OnItemClickListener(){public void onItemClick(AdapterView<?> parent,View view,int position,long id){
            Entry e=shown.get(position);
            if(mode.equals("movie"))details(e);else play(e);
        }});
    }
    void rows(List<Entry> values,String label){
        shown.clear();shown.addAll(values);
        final ArrayList<Entry> cards=new ArrayList<Entry>(values);
        final String section=mode;
        list.setAdapter(new BaseAdapter(){
            public int getCount(){return cards.size();}
            public Entry getItem(int position){return cards.get(position);}
            public long getItemId(int position){return position;}
            public View getView(int position,View recycled,ViewGroup parent){
                Entry entry=getItem(position);
                LinearLayout card=new LinearLayout(MainActivity.this);
                card.setGravity(Gravity.CENTER_VERTICAL);card.setPadding(dp(10),dp(8),dp(8),dp(8));
                card.setBackgroundDrawable(tile(0xff18334a,0xff294d68));
                card.setLayoutParams(new AbsListView.LayoutParams(-1,dp(100)));
                FrameLayout art=new FrameLayout(MainActivity.this);
                art.setBackgroundDrawable(tile(section.equals("live")?0xff126582:0xff594784,0xff477791));
                card.addView(art,new LinearLayout.LayoutParams(dp(58),-1));
                TextView symbol=text(section.equals("live")?String.format(Locale.US,"%02d",position+1):"▶",22);
                symbol.setGravity(Gravity.CENTER);art.addView(symbol,new FrameLayout.LayoutParams(-1,-1));
                if(!entry.poster.isEmpty()){
                    ImageView image=new ImageView(MainActivity.this);image.setScaleType(section.equals("live")?ImageView.ScaleType.FIT_CENTER:ImageView.ScaleType.CENTER_CROP);
                    art.addView(image,new FrameLayout.LayoutParams(-1,-1));loadPoster(entry.poster,image);
                }
                LinearLayout details=new LinearLayout(MainActivity.this);details.setOrientation(LinearLayout.VERTICAL);
                card.addView(details,new LinearLayout.LayoutParams(0,-2,1));
                TextView title=text(entry.name,17);title.setMaxLines(2);title.setEllipsize(android.text.TextUtils.TruncateAt.END);details.addView(title);
                TextView category=text(entry.group.isEmpty()?"点播":entry.group,13);category.setSingleLine(true);category.setTextColor(0xff9ed5eb);details.addView(category);
                return card;
            }
        });
        note(label+" · "+values.size()+" 项");
        if(!values.isEmpty()){list.setSelection(0);list.requestFocus();}
    }
    final android.util.LruCache<String,android.graphics.Bitmap> posters=new android.util.LruCache<String,android.graphics.Bitmap>(40);
    final ThreadPoolExecutor artwork=new ThreadPoolExecutor(2,2,0L,TimeUnit.MILLISECONDS,
        new ArrayBlockingQueue<Runnable>(20),new ThreadPoolExecutor.DiscardOldestPolicy());
    void loadPoster(final String url,final ImageView target){
        if(!url.startsWith("https://")&&!url.startsWith("http://"))return;
        android.graphics.Bitmap cached=posters.get(url);
        if(cached!=null){target.setImageBitmap(cached);return;}
        artwork.execute(new Runnable(){public void run(){
            HttpURLConnection connection=null;
            try{
                connection=Net.open(url,null);
                byte[] bytes;
                InputStream input=connection.getInputStream();
                try{bytes=Net.read(input,2*1024*1024);}finally{input.close();}
                android.graphics.BitmapFactory.Options options=new android.graphics.BitmapFactory.Options();
                options.inJustDecodeBounds=true;
                android.graphics.BitmapFactory.decodeByteArray(bytes,0,bytes.length,options);
                if(options.outWidth<=0||options.outHeight<=0)return;
                options.inSampleSize=1;
                while(Math.max(options.outWidth,options.outHeight)/options.inSampleSize>256)options.inSampleSize*=2;
                options.inJustDecodeBounds=false;
                final android.graphics.Bitmap bitmap=android.graphics.BitmapFactory.decodeByteArray(bytes,0,bytes.length,options);
                if(bitmap!=null){posters.put(url,bitmap);runOnUiThread(new Runnable(){public void run(){if(!isFinishing())target.setImageBitmap(bitmap);}});}
            }catch(Exception ignored){}finally{if(connection!=null)connection.disconnect();}
        }});
    }
    String asset(String name)throws Exception{InputStream in=getAssets().open(name);try{return new String(Net.read(in,8*1024*1024),"UTF-8");}finally{in.close();}}
    String setting(String key,String fallback){return prefs.getString(key,fallback);}
    String api(){return setting("vod",defaultApi);}
    ArrayList<Entry> parseLive(String content)throws Exception{
        ArrayList<Entry> values=new ArrayList<Entry>();String name="",category="直播",logo="";
        for(String line:content.replace("\r","").split("\n")){
            line=line.trim();
            if(line.startsWith("#EXTINF")){
                int comma=line.indexOf(',');name=comma>=0?line.substring(comma+1).trim():"直播";
                java.util.regex.Matcher m=java.util.regex.Pattern.compile("group-title=\"([^\"]*)\"").matcher(line);
                category=m.find()?m.group(1):"直播";
                java.util.regex.Matcher icon=java.util.regex.Pattern.compile("tvg-logo=\\\"([^\\\"]*)\\\"").matcher(line);
                logo=icon.find()?icon.group(1):"";
            }else if(line.contains(",#genre#")){category=line.substring(0,line.indexOf(",#genre#"));}
            else if(!line.isEmpty()&&!line.startsWith("#")){
                String url=line;
                if(!url.startsWith("http://")&&!url.startsWith("https://")){
                    int comma=line.indexOf(',');if(comma<0)continue;name=line.substring(0,comma);url=line.substring(comma+1).trim();
                }
                if(url.startsWith("http://")||url.startsWith("https://")){Entry entry=new Entry(name.isEmpty()?"直播":name,url,"",category);entry.poster=logo;values.add(entry);}
                name="";logo="";
            }
        }
        if(values.isEmpty())throw new IOException("未找到 HTTP/HTTPS 频道，请填写 M3U 或频道TXT地址");
        return values;
    }
    void loadLive(){
        final int ticket=++requestId;mode="live";note("正在读取直播列表…");
        executor.execute(new Runnable(){public void run(){try{
            ArrayList<Entry> values=new ArrayList<Entry>();
            String custom=setting("live","").trim();
            if(custom.isEmpty()){
                for(String name:new String[]{"live-snapshot.m3u","live-global.m3u","live-candidates.m3u"})values.addAll(parseLive(asset(name)));
            }else values=parseLive(Net.text(custom));
            final ArrayList<Entry> result=new ArrayList<Entry>();HashSet<String> urls=new HashSet<String>();
            for(Entry e:values)if(urls.add(e.url))result.add(e);
            runOnUiThread(new Runnable(){public void run(){if(ticket!=requestId||isFinishing())return;channels.clear();channels.addAll(result);filterLive();}});
        }catch(final Exception e){error(ticket,e);}}});
    }
    void filterLive(){ArrayList<Entry> filtered=new ArrayList<Entry>();for(Entry e:channels)if(group.equals("全部")||e.group.equals(group))filtered.add(e);rows(filtered,"直播 / "+group);}
    void groups(){
        if(!mode.equals("live")){Toast.makeText(this,"先切换到直播再选择分类",Toast.LENGTH_SHORT).show();return;}
        LinkedHashSet<String> available=new LinkedHashSet<String>();
        for(Entry e:channels)available.add(e.group);
        LinkedHashSet<String> set=new LinkedHashSet<String>();set.add("全部");
        for(String name:new String[]{"央视","卫视","海外","娱乐","体育","NBA","新闻","电影","卡通动漫","音乐","文化","生活健康","游戏直播","地方台","其他"})
            if(available.remove(name))set.add(name);
        set.addAll(available);
        if(!set.contains("NBA"))set.add("NBA 官方入口");
        final String[] names=set.toArray(new String[set.size()]);
        new AlertDialog.Builder(this).setTitle("直播分类").setItems(names,new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int i){
            if(names[i].equals("NBA 官方入口")){
                try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse("https://www.nba.com/watch")));}
                catch(ActivityNotFoundException e){note("此设备没有可打开 NBA 官网的浏览器");}
                return;
            }
            group=names[i];filterLive();
        }}).show();
    }
    String query(String api,String fields){return api+(api.contains("?")?"&":"?")+fields;}
    void loadMovies(){
        final int ticket=++requestId;mode="movie";note("正在读取点播列表…");
        executor.execute(new Runnable(){public void run(){try{
            if(api().trim().isEmpty())throw new IOException("请在配置中填写有权使用的 MacCMS JSON 点播接口");
            JSONObject json=new JSONObject(Net.text(query(api(),"ac=detail&pg="+moviePage+"&wd="+URLEncoder.encode(movieQuery,"UTF-8"))));
            final ArrayList<Entry> result=movieEntries(json);final int pages=Math.max(1,json.optInt("pagecount",1));
            runOnUiThread(new Runnable(){public void run(){if(ticket!=requestId||isFinishing())return;movies.clear();movies.addAll(result);moviePages=pages;rows(movies,"点播 第 "+moviePage+" / "+moviePages+" 页");}});
        }catch(final Exception e){error(ticket,e);}}});
    }
    ArrayList<Entry> movieEntries(JSONObject json)throws Exception{
        JSONArray values=json.optJSONArray("list");if(values==null)throw new IOException("接口没有返回 JSON list 列表");
        ArrayList<Entry> result=new ArrayList<Entry>();
        for(int i=0;i<values.length();i++){JSONObject item=values.getJSONObject(i);Entry entry=new Entry(item.optString("vod_name","未命名"),item.optString("vod_play_url"),item.optString("vod_id"),item.optString("type_name"));entry.poster=item.optString("vod_pic");result.add(entry);}
        return result;
    }
    void details(final Entry entry){
        if(!entry.url.isEmpty()){episodes(entry);return;}
        final int ticket=++requestId;note("正在读取剧集…");
        executor.execute(new Runnable(){public void run(){try{
            final ArrayList<Entry> result=movieEntries(new JSONObject(Net.text(query(api(),"ac=detail&ids="+URLEncoder.encode(entry.id,"UTF-8")))));
            if(result.isEmpty())throw new IOException("未找到剧集");
            runOnUiThread(new Runnable(){public void run(){if(ticket==requestId&&!isFinishing())episodes(result.get(0));}});
        }catch(final Exception e){error(ticket,e);}}});
    }
    void episodes(Entry entry){
        ++requestId;ArrayList<Entry> result=new ArrayList<Entry>();int source=0;
        for(String line:entry.url.split("\\$\\$\\$")){source++;for(String part:line.split("#")){
            int split=part.indexOf('$');String name=split>=0?part.substring(0,split):"播放";String url=split>=0?part.substring(split+1):part;
            if(url.startsWith("http://")||url.startsWith("https://"))result.add(new Entry(entry.name+" · "+name,url,"","线路"+source));
        }}
        if(result.isEmpty()){note("此条目没有可直接播放的 HTTP 视频地址");return;}
        mode="episodes";rows(result,entry.name+" / 剧集");
    }
    void error(final int ticket,final Exception e){runOnUiThread(new Runnable(){public void run(){if(ticket==requestId&&!isFinishing())note("读取失败："+e.getClass().getSimpleName()+" "+e.getMessage()+"；可在配置中切换源");}});}
    void search(){
        final EditText input=new EditText(this);input.setSingleLine(true);input.setHint(mode.equals("live")?"频道名称":"电影或电视剧名称");
        new AlertDialog.Builder(this).setTitle("搜索").setView(input).setPositiveButton("搜索",new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int w){
            String word=input.getText().toString().trim();if(mode.equals("live")){ArrayList<Entry> result=new ArrayList<Entry>();for(Entry e:channels)if(e.name.toLowerCase(Locale.US).contains(word.toLowerCase(Locale.US)))result.add(e);rows(result,"频道搜索");}
            else{movieQuery=word;moviePage=1;loadMovies();}
        }}).setNeutralButton("语音输入",new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int w){
            Intent voice=new Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            voice.putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL,android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            voice.putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE,"zh-CN");
            try{startActivityForResult(voice,91);}
            catch(ActivityNotFoundException e){Toast.makeText(MainActivity.this,"设备没有语音识别服务，可使用键盘搜索",Toast.LENGTH_LONG).show();search();}
        }}).setNegativeButton("取消",null).show();
    }
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(request!=91||result!=RESULT_OK||data==null)return;
        ArrayList<String> matches=data.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS);
        if(matches==null||matches.isEmpty())return;
        String word=matches.get(0).trim();
        if(word.isEmpty())return;
        if(mode.equals("live")){
            ArrayList<Entry> filtered=new ArrayList<Entry>();
            for(Entry e:channels)if(e.name.toLowerCase(Locale.US).contains(word.toLowerCase(Locale.US)))filtered.add(e);
            rows(filtered,"频道搜索");
        }else{movieQuery=word;moviePage=1;loadMovies();}
    }
    void direct(){
        final EditText input=new EditText(this);input.setSingleLine(true);input.setHint("https://…/video.m3u8 或 .mp4");input.setText(setting("direct",""));
        new AlertDialog.Builder(this).setTitle("播放视频地址").setView(input).setPositiveButton("播放",new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int w){String url=input.getText().toString().trim();prefs.edit().putString("direct",url).apply();play(new Entry("视频地址",url,"",""));}}).setNegativeButton("取消",null).show();
    }
    EditText field(LinearLayout root,String label,String value){root.addView(text(label,17));EditText input=new EditText(this);input.setSingleLine(true);input.setText(value);root.addView(input);return input;}
    boolean address(String value){return value.isEmpty()||value.startsWith("http://")||value.startsWith("https://");}
    void config(){
        LinearLayout content=column();content.setPadding(dp(16),dp(8),dp(16),dp(8));
        content.addView(text("直播留空使用内置列表；点播支持 MacCMS JSON。请使用有权访问的源。",17));
        final EditText live=field(content,"直播列表 URL（M3U / 频道TXT）",setting("live",""));
        final EditText vod=field(content,"点播 API URL",api());
        ScrollView scroll=new ScrollView(this);scroll.addView(content);
        final AlertDialog dialog=new AlertDialog.Builder(this).setTitle("视频源配置").setView(scroll).setPositiveButton("保存",null).setNeutralButton("恢复内置",new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int w){prefs.edit().remove("live").remove("vod").apply();mode="live";group="全部";loadLive();}}).setNegativeButton("取消",null).create();
        dialog.show();dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener(){public void onClick(View v){
            String a=live.getText().toString().trim(),b=vod.getText().toString().trim();
            if(!address(a)||!address(b)){Toast.makeText(MainActivity.this,"地址需以 http:// 或 https:// 开头",Toast.LENGTH_LONG).show();return;}
            prefs.edit().putString("live",a).putString("vod",b).apply();dialog.dismiss();group="全部";if(mode.equals("live"))loadLive();else{mode="movie";moviePage=1;loadMovies();}
        }});
    }
    void about(){new AlertDialog.Builder(this).setTitle("敖丙视界兼容版 "+VERSION).setMessage("开发者：Rios敖丙\n\n当前 API："+Build.VERSION.SDK_INT+"\n架构："+Build.CPU_ABI+"\n\n适配 API19+，使用系统播放器。高码率、HEVC及部分直播格式能否播放，取决于盒子硬件和固件。直播列表为候选快照，源站可能变动。\n\n本兼容版采用 GPL-3.0 许可，源列表沿用原有来源署名；其他版本第三方许可不变。").setPositiveButton("知道了",null).show();}
    void play(final Entry entry){
        try{
            if(!entry.url.startsWith("http://")&&!entry.url.startsWith("https://"))throw new IOException("此地址不是可直接播放的 HTTP 视频");
            ++requestId;beforePlayback=new ArrayList<Entry>(shown);currentName=entry.name;currentUrl=entry.url;
            currentChannel=-1;if(mode.equals("live"))for(int i=0;i<channels.size();i++)if(channels.get(i).url.equals(entry.url)){currentChannel=i;break;}
            if(proxy==null)proxy=new StreamProxy();
            final String local=proxy.url(entry.url);
            FrameLayout screen=new FrameLayout(this);screen.setBackgroundColor(Color.BLACK);
            video=new VideoView(this);FrameLayout.LayoutParams vp=new FrameLayout.LayoutParams(-1,-1,Gravity.CENTER);screen.addView(video,vp);
            status=text("正在连接："+entry.name+"\n返回：频道列表；菜单：播放操作",19);status.setBackgroundColor(0x99000000);
            FrameLayout.LayoutParams sp=new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM);screen.addView(status,sp);
            playingEpisodes=null;
            if(mode.equals("episodes")&&!shown.isEmpty()){
                LinearLayout layout=new LinearLayout(this);
                layout.addView(screen,new LinearLayout.LayoutParams(0,-1,1));
                LinearLayout side=column();side.addView(text("选集",22));
                final ArrayList<Entry> choices=new ArrayList<Entry>(shown);
                ListView episodeList=new ListView(this);
                playingEpisodes=episodeList;
                ArrayList<String> labels=new ArrayList<String>();
                int selected=0;
                for(int i=0;i<choices.size();i++){
                    Entry choice=choices.get(i);
                    boolean active=choice.url.equals(entry.url)&&choice.name.equals(entry.name);
                    if(active)selected=i;
                    labels.add((active?"▶ ":"")+choice.name);
                }
                episodeList.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_list_item_1,labels));
                episodeList.setOnItemClickListener(new AdapterView.OnItemClickListener(){public void onItemClick(AdapterView<?> parent,View view,int position,long id){
                    if(video!=null)video.stopPlayback();
                    play(choices.get(position));
                }});
                side.addView(episodeList,new LinearLayout.LayoutParams(-1,0,1));
                layout.addView(side,new LinearLayout.LayoutParams(dp(245),-1));
                setContentView(layout);episodeList.setSelection(selected);
            }else setContentView(screen);
            playing=true;
            final VideoView attempt = video;
            final Runnable timeout = new Runnable(){public void run(){
                if (video == attempt) {
                    attempt.stopPlayback();
                    note("连接超时 · 按返回换集，或按菜单重试");
                }
            }};
            attempt.postDelayed(timeout, 25000);
            video.setOnPreparedListener(new MediaPlayer.OnPreparedListener(){public void onPrepared(MediaPlayer mp){attempt.removeCallbacks(timeout);if(video==attempt){note(entry.name+"  ·  确定暂停/继续；菜单查看更多");attempt.start();}}});
            video.setOnErrorListener(new MediaPlayer.OnErrorListener(){public boolean onError(MediaPlayer mp,int what,int extra){attempt.removeCallbacks(timeout);if(video==attempt)note("播放失败（"+what+" / "+extra+"），可按菜单重试或用其他播放器打开");return true;}});
            video.setOnCompletionListener(new MediaPlayer.OnCompletionListener(){public void onCompletion(MediaPlayer mp){note("播放结束 · 返回选择其他内容");}});
            video.setVideoURI(Uri.parse(local));video.requestFocus();video.start();
        }catch(Exception e){if(playing)backFromPlayer();note("无法播放："+e.getMessage());}
    }
    void backFromPlayer(){++requestId;if(video!=null)video.stopPlayback();ArrayList<Entry> saved=beforePlayback;home();if(saved!=null)rows(saved,mode.equals("live")?"直播":"点播 / 剧集");}
    void playbackMenu(){
        new AlertDialog.Builder(this).setTitle(currentName).setItems(new String[]{"暂停 / 继续","后退15秒","前进15秒","使用其他播放器","返回列表","重新连接"},new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int which){
            if(which==0)toggle();else if(which==1)seek(-15000);else if(which==2)seek(15000);else if(which==3){
                Intent intent=new Intent(Intent.ACTION_VIEW);intent.setDataAndType(Uri.parse(currentUrl),"video/*");
                if(intent.resolveActivity(getPackageManager())!=null){if(video!=null)video.pause();startActivity(intent);}else Toast.makeText(MainActivity.this,"没有其他可用的视频播放器",Toast.LENGTH_LONG).show();
            }else if(which==4)backFromPlayer();
            else {if(video!=null)video.stopPlayback();play(new Entry(currentName,currentUrl,"",""));}
        }}).show();
    }
    void toggle(){if(video!=null){if(video.isPlaying()){video.pause();note(currentName+" · 已暂停");}else{video.start();note(currentName);}}}
    void seek(int delta){if(video!=null&&video.getDuration()>0)video.seekTo(Math.max(0,Math.min(video.getDuration(),video.getCurrentPosition()+delta)));}
    void changeChannel(int delta){if(currentChannel<0||channels.isEmpty())return;int index=(currentChannel+delta+channels.size())%channels.size();if(video!=null)video.stopPlayback();play(channels.get(index));}
    @Override public boolean onKeyDown(int key,KeyEvent event){
        if(playing){
            if(key==KeyEvent.KEYCODE_MENU){playbackMenu();return true;}
            if(playingEpisodes!=null&&key==KeyEvent.KEYCODE_DPAD_RIGHT&&!playingEpisodes.hasFocus()){
                playingEpisodes.requestFocus();return true;
            }
            if(playingEpisodes!=null&&playingEpisodes.hasFocus()){
                if(key==KeyEvent.KEYCODE_DPAD_LEFT){video.requestFocus();return true;}
                return super.onKeyDown(key,event);
            }
            if(key==KeyEvent.KEYCODE_DPAD_CENTER||key==KeyEvent.KEYCODE_ENTER||key==KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE){toggle();return true;}
            if(key==KeyEvent.KEYCODE_DPAD_LEFT){seek(-15000);return true;}if(key==KeyEvent.KEYCODE_DPAD_RIGHT){seek(15000);return true;}
            if(key==KeyEvent.KEYCODE_DPAD_UP){changeChannel(-1);return true;}if(key==KeyEvent.KEYCODE_DPAD_DOWN){changeChannel(1);return true;}
        }
        return super.onKeyDown(key,event);
    }
    @Override public void onBackPressed(){if(playing)backFromPlayer();else if(mode.equals("episodes")){mode="movie";rows(movies,"点播 第 "+moviePage+" 页");}else new AlertDialog.Builder(this).setMessage("退出敖丙视界？").setPositiveButton("退出",new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int w){finish();}}).setNegativeButton("取消",null).show();}
    @Override protected void onPause(){if(video!=null)video.pause();super.onPause();}
    @Override protected void onDestroy(){++requestId;if(video!=null)video.stopPlayback();if(proxy!=null)proxy.close();executor.shutdownNow();artwork.shutdownNow();super.onDestroy();}
}
