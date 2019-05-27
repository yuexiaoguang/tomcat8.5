package org.apache.jasper.compiler;

import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import javax.servlet.ServletContext;

import org.apache.jasper.Constants;
import org.apache.jasper.JasperException;
import org.apache.tomcat.Jar;
import org.apache.tomcat.util.descriptor.tld.TaglibXml;
import org.apache.tomcat.util.descriptor.tld.TldParser;
import org.apache.tomcat.util.descriptor.tld.TldResourcePath;
import org.xml.sax.SAXException;

/**
 * 此类缓存已解析的TLD文件实例，以消除为引用它每个JSP都要解析相同的TLD的需要.
 * 它不能防止多个线程处理相同的内容, 新TLD, 但它确实确保每个线程在解析后将使用相同的TLD对象.
 */
public class TldCache {

    public static final String SERVLET_CONTEXT_ATTRIBUTE_NAME =
            TldCache.class.getName();

    private final ServletContext servletContext;
    private final Map<String,TldResourcePath> uriTldResourcePathMap = new HashMap<>();
    private final Map<TldResourcePath,TaglibXmlCacheEntry> tldResourcePathTaglibXmlMap =
            new HashMap<>();
    private final TldParser tldParser;


    public static TldCache getInstance(ServletContext servletContext) {
        if (servletContext == null) {
            throw new IllegalArgumentException(Localizer.getMessage(
                    "org.apache.jasper.compiler.TldCache.servletContextNull"));
        }
        return (TldCache) servletContext.getAttribute(SERVLET_CONTEXT_ATTRIBUTE_NAME);
    }


    public TldCache(ServletContext servletContext,
            Map<String, TldResourcePath> uriTldResourcePathMap,
            Map<TldResourcePath, TaglibXml> tldResourcePathTaglibXmlMap) {
        this.servletContext = servletContext;
        this.uriTldResourcePathMap.putAll(uriTldResourcePathMap);
        for (Entry<TldResourcePath, TaglibXml> entry : tldResourcePathTaglibXmlMap.entrySet()) {
            TldResourcePath tldResourcePath = entry.getKey();
            long lastModified[] = getLastModified(tldResourcePath);
            TaglibXmlCacheEntry cacheEntry = new TaglibXmlCacheEntry(
                    entry.getValue(), lastModified[0], lastModified[1]);
            this.tldResourcePathTaglibXmlMap.put(tldResourcePath, cacheEntry);
        }
        boolean validate = Boolean.parseBoolean(
                servletContext.getInitParameter(Constants.XML_VALIDATION_TLD_INIT_PARAM));
        String blockExternalString = servletContext.getInitParameter(
                Constants.XML_BLOCK_EXTERNAL_INIT_PARAM);
        boolean blockExternal;
        if (blockExternalString == null) {
            blockExternal = true;
        } else {
            blockExternal = Boolean.parseBoolean(blockExternalString);
        }
        tldParser = new TldParser(true, validate, blockExternal);
    }


    public TldResourcePath getTldResourcePath(String uri) {
        return uriTldResourcePathMap.get(uri);
    }


    public TaglibXml getTaglibXml(TldResourcePath tldResourcePath) throws JasperException {
        TaglibXmlCacheEntry cacheEntry = tldResourcePathTaglibXmlMap.get(tldResourcePath);
        if (cacheEntry == null) {
            return null;
        }
        long lastModified[] = getLastModified(tldResourcePath);
        if (lastModified[0] != cacheEntry.getWebAppPathLastModified() ||
                lastModified[1] != cacheEntry.getEntryLastModified()) {
            synchronized (cacheEntry) {
                if (lastModified[0] != cacheEntry.getWebAppPathLastModified() ||
                        lastModified[1] != cacheEntry.getEntryLastModified()) {
                    // Re-parse TLD
                    TaglibXml updatedTaglibXml;
                    try {
                        updatedTaglibXml = tldParser.parse(tldResourcePath);
                    } catch (IOException | SAXException e) {
                        throw new JasperException(e);
                    }
                    cacheEntry.setTaglibXml(updatedTaglibXml);
                    cacheEntry.setWebAppPathLastModified(lastModified[0]);
                    cacheEntry.setEntryLastModified(lastModified[1]);
                }
            }
        }
        return cacheEntry.getTaglibXml();
    }


    private long[] getLastModified(TldResourcePath tldResourcePath) {
        long[] result = new long[2];
        result[0] = -1;
        result[1] = -1;
        try {
            String webappPath = tldResourcePath.getWebappPath();
            if (webappPath != null) {
                // webappPath 将是 null, 对于包含TLD的 JAR, 在类路径上但不是Web应用程序的一部分
                URL url = servletContext.getResource(tldResourcePath.getWebappPath());
                URLConnection conn = url.openConnection();
                result[0] = conn.getLastModified();
                if ("file".equals(url.getProtocol())) {
                    // 读取上次修改时间会打开输入流，因此我们需要确保它再次关闭，否则TLD文件将被锁定，直到GC运行.
                    conn.getInputStream().close();
                }
            }
            try (Jar jar = tldResourcePath.openJar()) {
                if (jar != null) {
                    result[1] = jar.getLastModified(tldResourcePath.getEntryName());
                }
            }
        } catch (IOException e) {
            // Ignore (shouldn't happen)
        }
        return result;
    }

    private static class TaglibXmlCacheEntry {
        private volatile TaglibXml taglibXml;
        private volatile long webAppPathLastModified;
        private volatile long entryLastModified;

        public TaglibXmlCacheEntry(TaglibXml taglibXml, long webAppPathLastModified,
                long entryLastModified) {
            this.taglibXml = taglibXml;
            this.webAppPathLastModified = webAppPathLastModified;
            this.entryLastModified = entryLastModified;
        }

        public TaglibXml getTaglibXml() {
            return taglibXml;
        }

        public void setTaglibXml(TaglibXml taglibXml) {
            this.taglibXml = taglibXml;
        }

        public long getWebAppPathLastModified() {
            return webAppPathLastModified;
        }

        public void setWebAppPathLastModified(long webAppPathLastModified) {
            this.webAppPathLastModified = webAppPathLastModified;
        }

        public long getEntryLastModified() {
            return entryLastModified;
        }

        public void setEntryLastModified(long entryLastModified) {
            this.entryLastModified = entryLastModified;
        }
    }
}
