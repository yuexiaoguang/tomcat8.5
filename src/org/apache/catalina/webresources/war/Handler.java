package org.apache.catalina.webresources.war;

import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;

public class Handler extends URLStreamHandler {

    @Override
    protected URLConnection openConnection(URL u) throws IOException {
        return new WarURLConnection(u);
    }

    @Override
    protected void setURL(URL u, String protocol, String host, int port, String authority, String userInfo, String path,
            String query, String ref) {
        if (path.startsWith("file:") && !path.startsWith("file:/")) {
            // 处理安全策略文件中URL的问题.
            // 在 Windows, 安全策略文件中 ${catalina.[home|base]} 的使用, 在代码库URL的格式为 file:C:/... , 但其实应该是 file:/C:/...
            // 对于文件: 和 jar: URLs, JRE对此进行补偿. 它不会补偿 war:file:... URLs. 因此, 这里需要这样
            path = "file:/" + path.substring(5);
        }
        super.setURL(u, protocol, host, port, authority, userInfo, path, query, ref);
    }
}
