package org.apache.tomcat.util.scan;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.regex.Matcher;

import org.apache.tomcat.Jar;
import org.apache.tomcat.util.buf.UriUtil;

/**
 * 提供一种机制来获取实现{@link Jar}的对象.
 */
public class JarFactory {

    private JarFactory() {
        // Factory class. Hide public constructor.
    }


    public static Jar newInstance(URL url) throws IOException {
        String urlString = url.toString();
        if (urlString.startsWith("jar:file:")) {
            if (urlString.endsWith("!/")) {
                return new JarFileUrlJar(url, true);
            } else {
                return new JarFileUrlNestedJar(url);
            }
        } else if (urlString.startsWith("war:file:")) {
            URL jarUrl = UriUtil.warToJar(url);
            return new JarFileUrlNestedJar(jarUrl);
        } else if (urlString.startsWith("file:")) {
            return new JarFileUrlJar(url, false);
        } else {
            return new UrlJar(url);
        }
    }


    public static URL getJarEntryURL(URL baseUrl, String entryName)
            throws MalformedURLException {

        String baseExternal = baseUrl.toExternalForm();

        if (baseExternal.startsWith("jar")) {
            // 假设这是指向WAR中的JAR文件. Java不支持 jar:jar:file:... 因此转换到 Tomcat的 war:file:...
            baseExternal = baseExternal.replaceFirst("^jar:", "war:");
            baseExternal = baseExternal.replaceFirst("!/",
                    Matcher.quoteReplacement(UriUtil.getWarSeparator()));
        }

        return new URL("jar:" + baseExternal + "!/" + entryName);
    }
}
