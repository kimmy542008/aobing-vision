package com.rios.aobing.legacy;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.*;

/** Local-only HLS relay provides TLS1.2 for old system players. */
final class StreamProxy {
    final ServerSocket server;
    final ExecutorService workers = Executors.newFixedThreadPool(6);
    final String token = UUID.randomUUID().toString();
    volatile boolean closed;
    StreamProxy() throws IOException {
        server = new ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"));
        Thread thread = new Thread(new Runnable() { public void run() {
            while (!closed) try {
                final Socket socket = server.accept();
                workers.execute(new Runnable() { public void run() { serve(socket); } });
            } catch (IOException e) { if (!closed) close(); }
        } }, "aobing-relay");
        thread.setDaemon(true); thread.start();
    }
    String url(String remote) throws Exception {
        URL u = new URL(remote);
        if (!u.getProtocol().equals("http") && !u.getProtocol().equals("https")) throw new IOException("只支持 HTTP/HTTPS 视频");
        return "http://127.0.0.1:"+server.getLocalPort()+"/"+token+"/stream?u="+URLEncoder.encode(remote,"UTF-8");
    }
    String manifest(String text, URL base) throws Exception {
        StringBuilder out = new StringBuilder();
        Pattern uri = Pattern.compile("URI=\"([^\"]+)\"");
        for (String line : text.replace("\r", "").split("\n")) {
            line = line.trim();
            if (!line.isEmpty() && !line.startsWith("#")) line = url(new URL(base,line).toString());
            else if (line.startsWith("#")) {
                Matcher matcher = uri.matcher(line); StringBuffer updated = new StringBuffer();
                while (matcher.find()) matcher.appendReplacement(updated, Matcher.quoteReplacement("URI=\""+url(new URL(base,matcher.group(1)).toString())+"\""));
                matcher.appendTail(updated); line = updated.toString();
            }
            out.append(line).append('\n');
        }
        return out.toString();
    }
    void serve(Socket socket) {
        HttpURLConnection remote = null;
        try {
            socket.setSoTimeout(10000);
            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(),"UTF-8"));
            String first = reader.readLine();
            if (first == null) return;
            String[] request = first.split(" ");
            if (request.length < 2 || !(request[0].equals("GET") || request[0].equals("HEAD"))) return;
            String prefix = "/"+token+"/stream?u=";
            if (!request[1].startsWith(prefix)) return;
            String range = null, header; int count = 0;
            while ((header = reader.readLine()) != null && !header.isEmpty()) {
                if (++count > 64 || header.length() > 8192) return;
                if (header.toLowerCase(Locale.US).startsWith("range:")) range = header.substring(6).trim();
            }
            String address = URLDecoder.decode(request[1].substring(prefix.length()),"UTF-8");
            remote = Net.open(address, range);
            int status = remote.getResponseCode();
            OutputStream out = socket.getOutputStream();
            if (status != 200 && status != 206) { send(out,502,"text/plain",("源站返回 HTTP "+status).getBytes("UTF-8"),false); return; }
            String mime = remote.getContentType();
            boolean playlist = remote.getURL().getPath().toLowerCase(Locale.US).endsWith(".m3u8") || (mime != null && mime.toLowerCase(Locale.US).contains("mpegurl"));
            InputStream in = remote.getInputStream();
            try {
                if (playlist) {
                    byte[] body = manifest(new String(Net.read(in,4*1024*1024),"UTF-8"),remote.getURL()).getBytes("UTF-8");
                    send(out,200,"application/vnd.apple.mpegurl",body,request[0].equals("HEAD"));
                } else {
                    StringBuilder h = new StringBuilder("HTTP/1.1 "+status+" OK\r\nConnection: close\r\n");
                    if (mime != null) h.append("Content-Type: ").append(mime).append("\r\n");
                    for (String name : new String[]{"Content-Length","Content-Range","Accept-Ranges"}) {
                        String value = remote.getHeaderField(name);
                        if (value != null) h.append(name).append(": ").append(value).append("\r\n");
                    }
                    out.write(h.append("\r\n").toString().getBytes("UTF-8"));
                    if (!request[0].equals("HEAD")) { byte[] buffer = new byte[32768]; int n; while ((n=in.read(buffer))!=-1) out.write(buffer,0,n); }
                    out.flush();
                }
            } finally { in.close(); }
        } catch (Exception e) {
            try { send(socket.getOutputStream(),502,"text/plain","视频连接失败".getBytes("UTF-8"),false); } catch (Exception ignored) { }
        } finally {
            if (remote != null) remote.disconnect();
            try { socket.close(); } catch (IOException ignored) { }
        }
    }
    void send(OutputStream out,int code,String mime,byte[] body,boolean head) throws IOException {
        out.write(("HTTP/1.1 "+code+" OK\r\nContent-Type: "+mime+"\r\nContent-Length: "+body.length+"\r\nConnection: close\r\n\r\n").getBytes("UTF-8"));
        if (!head) out.write(body); out.flush();
    }
    void close() { closed=true; try { server.close(); } catch (IOException ignored) { } workers.shutdownNow(); }
}
