package com.rios.aobing.legacy;

import java.io.*;
import java.net.*;
import java.util.*;
import javax.net.ssl.*;
import org.conscrypt.Conscrypt;

/** API19 TLS transport. Certificate and hostname verification remain enabled. */
final class Net {
    static final class Tls extends SSLSocketFactory {
        final SSLSocketFactory delegate;
        Tls() throws Exception {
            SSLContext context = SSLContext.getInstance("TLS", Conscrypt.newProvider());
            context.init(null, null, null);
            delegate = context.getSocketFactory();
        }
        Socket configure(Socket socket) {
            if (socket instanceof SSLSocket) {
                SSLSocket ssl = (SSLSocket) socket;
                ArrayList<String> enabled = new ArrayList<String>();
                for (String s : ssl.getSupportedProtocols())
                    if (s.equals("TLSv1.2") || s.equals("TLSv1.3")) enabled.add(s);
                if (!enabled.isEmpty()) ssl.setEnabledProtocols(enabled.toArray(new String[enabled.size()]));
            }
            return socket;
        }
        public String[] getDefaultCipherSuites() { return delegate.getDefaultCipherSuites(); }
        public String[] getSupportedCipherSuites() { return delegate.getSupportedCipherSuites(); }
        public Socket createSocket(Socket s,String h,int p,boolean c)throws IOException{
            SSLSocket socket = (SSLSocket) delegate.createSocket(s,h,p,c);
            if (Conscrypt.isConscrypt(socket)) Conscrypt.setHostname(socket,h);
            return configure(socket);
        }
        public Socket createSocket(String h,int p)throws IOException{return configure(delegate.createSocket(h,p));}
        public Socket createSocket(String h,int p,InetAddress l,int lp)throws IOException{return configure(delegate.createSocket(h,p,l,lp));}
        public Socket createSocket(InetAddress h,int p)throws IOException{return configure(delegate.createSocket(h,p));}
        public Socket createSocket(InetAddress h,int p,InetAddress l,int lp)throws IOException{return configure(delegate.createSocket(h,p,l,lp));}
    }
    static HttpURLConnection open(String address, String range) throws Exception {
        for (int redirect = 0; redirect < 6; redirect++) {
            URL url = new URL(address);
            if (!url.getProtocol().equals("http") && !url.getProtocol().equals("https")) throw new IOException("只支持 HTTP/HTTPS 地址");
            HttpURLConnection c = (HttpURLConnection) url.openConnection();
            if (c instanceof HttpsURLConnection) ((HttpsURLConnection)c).setSSLSocketFactory(new Tls());
            c.setConnectTimeout(12000); c.setReadTimeout(20000); c.setInstanceFollowRedirects(false);
            c.setRequestProperty("User-Agent", "Mozilla/5.0 AobingTV/0.4.2");
            c.setRequestProperty("Accept-Encoding", "identity");
            if (range != null) c.setRequestProperty("Range", range);
            int code = c.getResponseCode();
            if (code >= 300 && code < 400 && code != 304) {
                String location = c.getHeaderField("Location");
                c.disconnect();
                if (location == null) throw new IOException("跳转缺少地址");
                address = new URL(url, location).toString();
            } else return c;
        }
        throw new IOException("跳转次数过多");
    }
    static byte[] read(InputStream in, int max) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192]; int n;
        while ((n = in.read(buffer)) != -1) {
            if (out.size() + n > max) throw new IOException("列表超过大小限制");
            out.write(buffer, 0, n);
        }
        return out.toByteArray();
    }
    static String text(String address) throws Exception {
        HttpURLConnection c = open(address, null);
        try {
            if (c.getResponseCode() != 200) throw new IOException("HTTP " + c.getResponseCode());
            InputStream in = c.getInputStream();
            try { return new String(read(in, 8 * 1024 * 1024), "UTF-8"); } finally { in.close(); }
        } finally { c.disconnect(); }
    }
}
