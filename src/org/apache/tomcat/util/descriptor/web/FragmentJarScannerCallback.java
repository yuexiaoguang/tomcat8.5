package org.apache.tomcat.util.descriptor.web;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import org.apache.tomcat.Jar;
import org.apache.tomcat.JarScannerCallback;
import org.xml.sax.InputSource;

/**
* 回调处理web-fragment.xml描述符.
*/
public class FragmentJarScannerCallback implements JarScannerCallback {

    private static final String FRAGMENT_LOCATION =
        "META-INF/web-fragment.xml";
    private final WebXmlParser webXmlParser;
    private final boolean delegate;
    private final boolean parseRequired;
    private final Map<String,WebXml> fragments = new HashMap<>();
    private boolean ok  = true;

    public FragmentJarScannerCallback(WebXmlParser webXmlParser, boolean delegate,
            boolean parseRequired) {
        this.webXmlParser = webXmlParser;
        this.delegate = delegate;
        this.parseRequired = parseRequired;
    }


    @Override
    public void scan(Jar jar, String webappPath, boolean isWebapp) throws IOException {

        InputStream is = null;
        WebXml fragment = new WebXml();
        fragment.setWebappJar(isWebapp);
        fragment.setDelegate(delegate);

        try {
            // 仅检查Web应用程序JAR的web-fragment.xml文件.
            // 如果永远不会使用web-fragment.xml文件, 则不需要对其进行解析.
            if (isWebapp && parseRequired) {
                is = jar.getInputStream(FRAGMENT_LOCATION);
            }

            if (is == null) {
                // 如果没有web.xml，则普通JAR对分发无影响
                fragment.setDistributable(true);
            } else {
                String fragmentUrl = jar.getURL(FRAGMENT_LOCATION);
                InputSource source = new InputSource(fragmentUrl);
                source.setByteStream(is);
                if (!webXmlParser.parseWebXml(source, fragment, true)) {
                    ok = false;
                }
            }
        } finally {
            fragment.setURL(jar.getJarFileURL());
            if (fragment.getName() == null) {
                fragment.setName(fragment.getURL().toString());
            }
            fragment.setJarName(extractJarFileName(jar.getJarFileURL()));
            fragments.put(fragment.getName(), fragment);
        }
    }


    private String extractJarFileName(URL input) {
        String url = input.toString();
        if (url.endsWith("!/")) {
            // Remove it
            url = url.substring(0, url.length() - 2);
        }

        // 文件名将是最后一个 / 后的内容
        return url.substring(url.lastIndexOf('/') + 1);
    }


    @Override
    public void scan(File file, String webappPath, boolean isWebapp) throws IOException {

        WebXml fragment = new WebXml();
        fragment.setWebappJar(isWebapp);
        fragment.setDelegate(delegate);

        File fragmentFile = new File(file, FRAGMENT_LOCATION);
        try {
            if (fragmentFile.isFile()) {
                try (InputStream stream = new FileInputStream(fragmentFile)) {
                    InputSource source =
                        new InputSource(fragmentFile.toURI().toURL().toString());
                    source.setByteStream(stream);
                    if (!webXmlParser.parseWebXml(source, fragment, true)) {
                        ok = false;
                    }
                }
            } else {
                // 如果没有web.xml，普通文件夹对distributable没有影响
                fragment.setDistributable(true);
            }
        } finally {
            fragment.setURL(file.toURI().toURL());
            if (fragment.getName() == null) {
                fragment.setName(fragment.getURL().toString());
            }
            fragment.setJarName(file.getName());
            fragments.put(fragment.getName(), fragment);
        }
    }


    @Override
    public void scanWebInfClasses() {
        // NO-OP. 不处理在WEB-INF类中解压缩的碎片, 主要是因为如果有多个片段, 则无法处理多个web-fragment.xml文件.
    }

    public boolean isOk() {
        return ok;
    }

    public Map<String,WebXml> getFragments() {
        return fragments;
    }
}
