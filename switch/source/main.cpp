// SPDX-License-Identifier: GPL-3.0-or-later
// Rios敖丙. Independent Switch homebrew prototype; no console firmware changes.
#include <switch.h>
#include <SDL2/SDL.h>
#include <SDL2/SDL_ttf.h>
#include <curl/curl.h>
#include <nlohmann/json.hpp>
#include <algorithm>
#include <atomic>
#include <chrono>
#include <fstream>
#include <functional>
#include <future>
#include <mutex>
#include <sstream>
#include <thread>
#include <vector>
extern "C" {
#include <libavformat/avformat.h>
#include <libavcodec/avcodec.h>
#include <libswscale/swscale.h>
#include <libswresample/swresample.h>
}
using json=nlohmann::json;
using Clock=std::chrono::steady_clock;
static const char* base="sdmc:/switch/aobing-vision/";
struct Item {std::string name,url,id,group;};
static SDL_Renderer* renderer;
static TTF_Font* font;
static PadState pad;
static std::string api,notice;
static int tab=0,selected=0,groupIndex=0;
static bool episodeMode=false;
static std::vector<Item> channels,items;
static std::vector<std::string> groups={"全部"};
static std::string readFile(const std::string& path){std::ifstream f(path);return {std::istreambuf_iterator<char>(f),{}};}
static size_t receive(char* p,size_t a,size_t b,void* data){auto& s=*static_cast<std::string*>(data);size_t n=a*b;if(s.size()+n>8*1024*1024)return 0;s.append(p,n);return n;}
static std::string get(const std::string& url){
    CURL* c=curl_easy_init();if(!c)throw std::runtime_error("网络初始化失败");std::string result;
    curl_easy_setopt(c,CURLOPT_URL,url.c_str());curl_easy_setopt(c,CURLOPT_FOLLOWLOCATION,1L);
    curl_easy_setopt(c,CURLOPT_PROTOCOLS,(long)(CURLPROTO_HTTP|CURLPROTO_HTTPS));
    curl_easy_setopt(c,CURLOPT_REDIR_PROTOCOLS,(long)(CURLPROTO_HTTP|CURLPROTO_HTTPS));
    curl_easy_setopt(c,CURLOPT_CAINFO,"romfs:/cacert.pem");curl_easy_setopt(c,CURLOPT_CONNECTTIMEOUT,8L);
    curl_easy_setopt(c,CURLOPT_TIMEOUT,20L);curl_easy_setopt(c,CURLOPT_FAILONERROR,1L);curl_easy_setopt(c,CURLOPT_NOSIGNAL,1L);
    curl_easy_setopt(c,CURLOPT_WRITEFUNCTION,receive);curl_easy_setopt(c,CURLOPT_WRITEDATA,&result);
    auto rc=curl_easy_perform(c);std::string error=curl_easy_strerror(rc);curl_easy_cleanup(c);
    if(rc!=CURLE_OK)throw std::runtime_error(error);return result;
}
static std::string field(const json& j,const char* key){if(!j.contains(key)||j[key].is_null())return "";return j[key].is_string()?j[key].get<std::string>():j[key].dump();}
static std::string query(const std::string& q){return api+(api.find('?')==std::string::npos?"?":"&")+q;}
static std::vector<Item> catalog(const std::string& q){
    if(api.empty())throw std::runtime_error("请按 X 配置点播 JSON 接口");
    auto j=json::parse(get(query(q)));std::vector<Item> out;
    if(!j.contains("list")||!j["list"].is_array())throw std::runtime_error("接口没有返回 list 数组");
    for(auto& e:j["list"])out.push_back({field(e,"vod_name"),field(e,"vod_play_url"),field(e,"vod_id"),field(e,"type_name")});return out;
}
static std::string attribute(const std::string& line,const std::string& name){auto a=line.find(name+"=\"");if(a==std::string::npos)return "";a+=name.size()+2;auto b=line.find('"',a);return line.substr(a,b-a);}
static void readM3u(const std::string& content){
    std::istringstream lines(content);std::string line,name,group="直播";
    while(std::getline(lines,line)){
        if(!line.empty()&&line.back()=='\r')line.pop_back();
        if(line.rfind("#EXTINF",0)==0){auto p=line.find(',');name=p==std::string::npos?"直播":line.substr(p+1);group=attribute(line,"group-title");}
        else if(line.rfind("http://",0)==0||line.rfind("https://",0)==0){
            channels.push_back({name.empty()?"直播":name,line,"",group});
            if(std::find(groups.begin(),groups.end(),group)==groups.end())groups.push_back(group);
        }
    }
}
static void text(const std::string& value,int x,int y,SDL_Color color={235,245,255,255},int width=1180){
    SDL_Surface* s=TTF_RenderUTF8_Blended_Wrapped(font,value.c_str(),color,width);if(!s)return;
    SDL_Texture* t=SDL_CreateTextureFromSurface(renderer,s);SDL_Rect r={x,y,s->w,s->h};SDL_RenderCopy(renderer,t,nullptr,&r);SDL_DestroyTexture(t);SDL_FreeSurface(s);
}
static void rect(int x,int y,int w,int h,SDL_Color c){SDL_SetRenderDrawColor(renderer,c.r,c.g,c.b,c.a);SDL_Rect r={x,y,w,h};SDL_RenderFillRect(renderer,&r);}
static void screen(){
    rect(0,0,1280,720,{9,19,35,255});text("敖丙视界  |  Switch 0.1.1",36,22);
    text("L / R  切换栏目       X  设置       +  退出",650,26,{150,190,210,255});
    const char* tabs[]={"首页","直播","电影","剧集"};
    for(int i=0;i<4;i++){rect(36+i*160,80,145,48,i==tab?SDL_Color{8,155,183,255}:SDL_Color{25,47,70,255});text(tabs[i],60+i*160,88);}
    rect(36,148,1208,140,{16,48,74,255});
    text(items.empty()?"欢迎使用敖丙视界":items[std::min(selected,(int)items.size()-1)].name,60,180);
    text(episodeMode?"选集 · A 播放 · B 返回":tab==1?"直播 · Y 选择分类 · A 播放":"点播 · A 选择影片 · Y 搜索",60,236,{141,209,227,255});
    int start=selected/8*8;
    for(int i=start;i<(int)items.size()&&i<start+8;i++){
        int col=(i-start)%4,row=(i-start)/4,x=36+col*308,y=312+row*138;
        rect(x,y,288,120,i==selected?SDL_Color{12,124,157,255}:SDL_Color{26,48,70,255});
        text(items[i].name,x+12,y+16,{240,248,255,255},260);text(items[i].group,x+12,y+83,{144,190,208,255},260);
    }
    text(notice,36,605,{247,207,130,255});
    text("A 确定    B 返回    方向键选择    仅使用有权播放的来源",36,666,{147,180,203,255});SDL_RenderPresent(renderer);
}
static std::string keyboard(const char* hint,const std::string& initial=""){
    SwkbdConfig config;char result[2048]={};if(R_FAILED(swkbdCreate(&config,0)))return "";
    swkbdConfigMakePresetDefault(&config);swkbdConfigSetHeaderText(&config,hint);swkbdConfigSetInitialText(&config,initial.c_str());
    swkbdConfigSetStringLenMax(&config,2047);Result rc=swkbdShow(&config,result,sizeof(result));swkbdClose(&config);return R_SUCCEEDED(rc)?result:"";
}
// Metadata requests run away from the render thread. Network timeouts remain bounded.
static bool load(std::function<std::vector<Item>()> fn){
    notice="正在连接…";auto future=std::async(std::launch::async,fn);
    while(future.wait_for(std::chrono::milliseconds(0))!=std::future_status::ready){if(!appletMainLoop())break;screen();SDL_Delay(16);}
    try{items=future.get();selected=0;notice=items.empty()?"当前分类暂无内容":"";return true;}
    catch(const std::exception& e){notice=std::string("读取失败：")+e.what();return false;}
}
struct Player {
    std::atomic<bool> stop{false};std::atomic<int64_t> deadline{0};std::mutex lock;
    std::vector<unsigned char> pixels;int w=0,h=0;bool fresh=false;std::string error;
    SDL_AudioDeviceID audio=0;
    static int64_t now(){return std::chrono::duration_cast<std::chrono::milliseconds>(Clock::now().time_since_epoch()).count();}
    static int interrupt(void* opaque){auto* p=static_cast<Player*>(opaque);return p->stop||now()>p->deadline;}
    void decode(std::string url){
        AVFormatContext* fmt=avformat_alloc_context();fmt->interrupt_callback={interrupt,this};deadline=now()+12000;
        AVDictionary* opts=nullptr;av_dict_set(&opts,"tls_verify","1",0);av_dict_set(&opts,"ca_file","romfs:/cacert.pem",0);
        std::string stage="连接视频";
        int rc=avformat_open_input(&fmt,url.c_str(),nullptr,&opts);av_dict_free(&opts);
        AVCodecContext *vc=nullptr,*ac=nullptr;SwsContext* scale=nullptr;SwrContext* resample=nullptr;
        AVPacket* packet=av_packet_alloc();AVFrame* frame=av_frame_alloc();int vi=-1,ai=-1;int64_t start=now();double first=-1;
        if(rc>=0){stage="识别视频格式";deadline=now()+30000;rc=avformat_find_stream_info(fmt,nullptr);}
        if(rc>=0){
            stage="初始化解码器";
            vi=av_find_best_stream(fmt,AVMEDIA_TYPE_VIDEO,-1,-1,nullptr,0);ai=av_find_best_stream(fmt,AVMEDIA_TYPE_AUDIO,-1,-1,nullptr,0);
            auto codec=[&](int index)->AVCodecContext*{if(index<0)return nullptr;auto* par=fmt->streams[index]->codecpar;const auto* c=avcodec_find_decoder(par->codec_id);if(!c)return nullptr;auto* ctx=avcodec_alloc_context3(c);avcodec_parameters_to_context(ctx,par);ctx->thread_count=2;if(avcodec_open2(ctx,c,nullptr)<0)avcodec_free_context(&ctx);return ctx;};
            vc=codec(vi);ac=codec(ai);if(!vc)rc=AVERROR_DECODER_NOT_FOUND;
            if(ac){AVChannelLayout stereo=AV_CHANNEL_LAYOUT_STEREO;swr_alloc_set_opts2(&resample,&stereo,AV_SAMPLE_FMT_S16,48000,&ac->ch_layout,ac->sample_fmt,ac->sample_rate,0,nullptr);if(!resample||swr_init(resample)<0)swr_free(&resample);}
        }
        while(rc>=0&&!stop){
            deadline=now()+12000;rc=av_read_frame(fmt,packet);if(rc<0)break;
            bool isVideo=packet->stream_index==vi;AVCodecContext* ctx=isVideo?vc:(packet->stream_index==ai?ac:nullptr);
            if(ctx&&avcodec_send_packet(ctx,packet)>=0)while(!stop&&avcodec_receive_frame(ctx,frame)>=0){
                if(isVideo){
                    double pts=frame->best_effort_timestamp==AV_NOPTS_VALUE?-1:frame->best_effort_timestamp*av_q2d(fmt->streams[vi]->time_base);
                    if(pts>=0){if(first<0){first=pts;start=now();}int64_t wait=(int64_t)((pts-first)*1000)-(now()-start);if(wait>2000||wait< -2000){first=pts;start=now();wait=0;}while(wait>0&&!stop){SDL_Delay((Uint32)std::min<int64_t>(wait,10));wait=(int64_t)((pts-first)*1000)-(now()-start);}}
                    int dw=std::min(frame->width,1280),dh=frame->height*dw/frame->width;
                    scale=sws_getCachedContext(scale,frame->width,frame->height,(AVPixelFormat)frame->format,dw,dh,AV_PIX_FMT_RGBA,SWS_FAST_BILINEAR,nullptr,nullptr,nullptr);
                    if(!scale){rc=AVERROR(ENOMEM);stop=true;break;}
                    std::vector<unsigned char> rgba(dw*dh*4);uint8_t* dst[]={rgba.data()};int stride[]={dw*4};sws_scale(scale,frame->data,frame->linesize,0,frame->height,dst,stride);
                    {std::lock_guard<std::mutex> guard(lock);pixels.swap(rgba);w=dw;h=dh;fresh=true;}
                }else if(resample&&audio){
                    int count=swr_get_out_samples(resample,frame->nb_samples);std::vector<uint8_t> pcm(count*4);uint8_t* dst[]={pcm.data()};
                    int n=swr_convert(resample,dst,count,(const uint8_t**)frame->extended_data,frame->nb_samples);
                    while(!stop&&SDL_GetQueuedAudioSize(audio)>48000*4)SDL_Delay(10);
                    if(n>0&&!stop)SDL_QueueAudio(audio,pcm.data(),n*4);
                }
                av_frame_unref(frame);
            }
            av_packet_unref(packet);
        }
        if(rc<0&&rc!=AVERROR_EOF&&!stop){char message[256];av_strerror(rc,message,sizeof(message));std::lock_guard<std::mutex> guard(lock);error=stage+"："+message;}
        av_frame_free(&frame);av_packet_free(&packet);avcodec_free_context(&vc);avcodec_free_context(&ac);sws_freeContext(scale);swr_free(&resample);avformat_close_input(&fmt);stop=true;
    }
};
static void play(const Item& item){
    Player p;SDL_AudioSpec spec{};spec.freq=48000;spec.format=AUDIO_S16SYS;spec.channels=2;spec.samples=2048;
    p.audio=SDL_OpenAudioDevice(nullptr,0,&spec,nullptr,0);if(p.audio)SDL_PauseAudioDevice(p.audio,0);
    std::thread thread(&Player::decode,&p,item.url);SDL_Texture* texture=nullptr;int tw=0,th=0;
    while(appletMainLoop()&&!p.stop){
        padUpdate(&pad);if(padGetButtonsDown(&pad)&(HidNpadButton_B|HidNpadButton_Plus)){p.stop=true;break;}
        rect(0,0,1280,720,{0,0,0,255});
        {std::lock_guard<std::mutex> guard(p.lock);if(p.fresh){if(tw!=p.w||th!=p.h){SDL_DestroyTexture(texture);tw=p.w;th=p.h;texture=SDL_CreateTexture(renderer,SDL_PIXELFORMAT_ABGR8888,SDL_TEXTUREACCESS_STREAMING,tw,th);}if(texture)SDL_UpdateTexture(texture,nullptr,p.pixels.data(),tw*4);p.fresh=false;}}
        if(texture){double fit=std::min(1280.0/tw,720.0/th);SDL_Rect r={(int)((1280-tw*fit)/2),(int)((720-th*fit)/2),(int)(tw*fit),(int)(th*fit)};SDL_RenderCopy(renderer,texture,nullptr,&r);}
        else text("正在连接视频…  B 返回",40,620);
        SDL_RenderPresent(renderer);SDL_Delay(10);
    }
    p.stop=true;thread.join();SDL_DestroyTexture(texture);if(p.audio){SDL_ClearQueuedAudio(p.audio);SDL_CloseAudioDevice(p.audio);}notice=p.error.empty()?"已返回列表":"播放失败："+p.error;
}
static void refresh(){
    episodeMode=false;
    if(tab==1){items.clear();for(const auto& c:channels)if(groupIndex==0||c.group==groups[groupIndex])items.push_back(c);selected=0;notice=items.empty()?"分类无频道；X 可设置直播订阅":"";return;}
    load([]{auto all=catalog("ac=detail&pg=1");if(tab==0)return all;std::vector<Item> out;for(auto& e:all){bool series=e.group.find("剧")!=std::string::npos;if((tab==3&&series)||(tab==2&&!series))out.push_back(e);}return out;});
}
static void activate(){
    if(items.empty())return;Item entry=items[selected];if(tab==1||episodeMode){play(entry);return;}
    if(entry.url.empty()){if(!load([entry]{return catalog("ac=detail&ids="+entry.id);})||items.empty())return;entry=items[0];}
    std::vector<Item> episodes;std::string body=entry.url;size_t start=0;int source=1;
    while(start<body.size()){
        auto end=body.find("$$$",start);auto line=body.substr(start,end-start);std::istringstream parts(line);std::string part;
        while(std::getline(parts,part,'#')){auto dollar=part.find('$');auto url=dollar==std::string::npos?part:part.substr(dollar+1);if(url.rfind("http://",0)==0||url.rfind("https://",0)==0)episodes.push_back({dollar==std::string::npos?"播放":part.substr(0,dollar),url,"","线路 "+std::to_string(source)});}
        if(end==std::string::npos)break;start=end+3;source++;
    }
    if(episodes.empty()){notice="没有可直接播放的 HTTP/HTTPS 地址";return;}items=episodes;selected=0;episodeMode=true;
}
int main(int,char**){
    socketInitializeDefault();romfsInit();plInitialize(PlServiceType_User);curl_global_init(CURL_GLOBAL_DEFAULT);avformat_network_init();
    padConfigureInput(1,HidNpadStyleSet_NpadStandard);padInitializeDefault(&pad);
    if(SDL_Init(SDL_INIT_VIDEO|SDL_INIT_AUDIO)<0)return 1;TTF_Init();
    PlFontData shared{};if(R_FAILED(plGetSharedFontByType(&shared,PlSharedFontType_ChineseSimplified)))plGetSharedFontByType(&shared,PlSharedFontType_Standard);
    font=TTF_OpenFontRW(SDL_RWFromConstMem(shared.address,shared.size),1,24);
    SDL_Window* window=SDL_CreateWindow("Aobing Vision",0,0,1280,720,0);renderer=SDL_CreateRenderer(window,-1,SDL_RENDERER_ACCELERATED|SDL_RENDERER_PRESENTVSYNC);
    if(!font||!renderer)return 2;
    api=readFile(std::string(base)+"vod-api.txt");while(!api.empty()&&(api.back()=='\n'||api.back()=='\r'))api.pop_back();
    std::string playlist=readFile(std::string(base)+"live.m3u");
    readM3u(playlist.empty()?readFile("romfs:/live.m3u"):playlist);tab=1;refresh();
    while(appletMainLoop()){
        padUpdate(&pad);uint64_t keys=padGetButtonsDown(&pad);if(keys&HidNpadButton_Plus)break;
        if(keys&HidNpadButton_R){tab=(tab+1)%4;refresh();}if(keys&HidNpadButton_L){tab=(tab+3)%4;refresh();}
        int delta=0;if(keys&HidNpadButton_Down)delta=4;if(keys&HidNpadButton_Up)delta=-4;if(keys&HidNpadButton_Right)delta=1;if(keys&HidNpadButton_Left)delta=-1;
        if(!items.empty())selected=std::max(0,std::min((int)items.size()-1,selected+delta));
        if(keys&HidNpadButton_A)activate();if(keys&HidNpadButton_B)refresh();
        if(keys&HidNpadButton_Y){if(tab==1){groupIndex=(groupIndex+1)%groups.size();refresh();notice=groups[groupIndex];}else{auto word=keyboard("搜索影片");if(!word.empty()){CURL* c=curl_easy_init();char* escaped=curl_easy_escape(c,word.c_str(),word.size());std::string q=std::string("ac=detail&wd=")+escaped;curl_free(escaped);curl_easy_cleanup(c);episodeMode=false;load([q]{return catalog(q);});}}}
        if(keys&HidNpadButton_X){
            auto value=keyboard("点播 JSON 接口地址（留空保留）",api);if(!value.empty()){api=value;std::ofstream(std::string(base)+"vod-api.txt")<<api;}
            auto live=keyboard("直播 M3U 订阅地址（留空保留）");if(!live.empty()){
                notice="正在更新直播订阅…";screen();try{auto content=get(live);channels.clear();groups={"全部"};readM3u(content);std::ofstream(std::string(base)+"live.m3u")<<content;groupIndex=0;}catch(const std::exception& e){notice=e.what();}
            }refresh();
        }
        screen();SDL_Delay(16);
    }
    SDL_DestroyRenderer(renderer);SDL_DestroyWindow(window);TTF_CloseFont(font);TTF_Quit();SDL_Quit();curl_global_cleanup();plExit();romfsExit();socketExit();return 0;
}
