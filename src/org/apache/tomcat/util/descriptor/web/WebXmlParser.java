package org.apache.tomcat.util.descriptor.web;

import java.io.IOException;
import java.net.URL;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.descriptor.DigesterFactory;
import org.apache.tomcat.util.descriptor.InputSourceUtil;
import org.apache.tomcat.util.descriptor.XmlErrorHandler;
import org.apache.tomcat.util.digester.Digester;
import org.apache.tomcat.util.res.StringManager;
import org.xml.sax.InputSource;
import org.xml.sax.SAXParseException;

public class WebXmlParser {

    private static final Log log = LogFactory.getLog(WebXmlParser.class);

    /**
     * 此包的字符串资源.
     */
    private static final StringManager sm =
        StringManager.getManager(Constants.PACKAGE_NAME);

    /**
     * 将使用<code>Digester</code>来处理Web应用程序部署描述符文件.
     */
    private final Digester webDigester;
    private final WebRuleSet webRuleSet;

    /**
     * 将使用<code>Digester</code>来处理Web片段部署描述符文件.
     */
    private final Digester webFragmentDigester;
    private final WebRuleSet webFragmentRuleSet;


    public WebXmlParser(boolean namespaceAware, boolean validation,
            boolean blockExternal) {
        webRuleSet = new WebRuleSet(false);
        webDigester = DigesterFactory.newDigester(validation,
                namespaceAware, webRuleSet, blockExternal);
        webDigester.getParser();

        webFragmentRuleSet = new WebRuleSet(true);
        webFragmentDigester = DigesterFactory.newDigester(validation,
                namespaceAware, webFragmentRuleSet, blockExternal);
        webFragmentDigester.getParser();
    }

    /**
     * 解析某个位置的Web描述符.
     *
     * @param url 位置; 如果为null, 则不会尝试加载
     * @param dest 要由解析操作填充的实例
     * @param fragment 指示描述符是Web应用程序, 还是Web片段
     * 
     * @return true 如果描述符已成功解析
     * @throws IOException 如果从URL读取有问题
     */
    public boolean parseWebXml(URL url, WebXml dest, boolean fragment) throws IOException {
        if (url == null) {
            return true;
        }
        InputSource source = new InputSource(url.toExternalForm());
        source.setByteStream(url.openStream());
        return parseWebXml(source, dest, fragment);
    }


    public boolean parseWebXml(InputSource source, WebXml dest,
            boolean fragment) {

        boolean ok = true;

        if (source == null) {
            return ok;
        }

        XmlErrorHandler handler = new XmlErrorHandler();

        Digester digester;
        WebRuleSet ruleSet;
        if (fragment) {
            digester = webFragmentDigester;
            ruleSet = webFragmentRuleSet;
        } else {
            digester = webDigester;
            ruleSet = webRuleSet;
        }

        digester.push(dest);
        digester.setErrorHandler(handler);

        if(log.isDebugEnabled()) {
            log.debug(sm.getString("webXmlParser.applicationStart",
                    source.getSystemId()));
        }

        try {
            digester.parse(source);

            if (handler.getWarnings().size() > 0 ||
                    handler.getErrors().size() > 0) {
                ok = false;
                handler.logFindings(log, source.getSystemId());
            }
        } catch (SAXParseException e) {
            log.error(sm.getString("webXmlParser.applicationParse",
                    source.getSystemId()), e);
            log.error(sm.getString("webXmlParser.applicationPosition",
                             "" + e.getLineNumber(),
                             "" + e.getColumnNumber()));
            ok = false;
        } catch (Exception e) {
            log.error(sm.getString("webXmlParser.applicationParse",
                    source.getSystemId()), e);
            ok = false;
        } finally {
            InputSourceUtil.close(source);
            digester.reset();
            ruleSet.recycle();
        }

        return ok;
    }


    /**
     * 设置用于创建描述符对象的ClassLoader.
     * 
     * @param classLoader 用于创建描述符对象的ClassLoader
     */
    public void setClassLoader(ClassLoader classLoader) {
        webDigester.setClassLoader(classLoader);
        webFragmentDigester.setClassLoader(classLoader);
    }
}