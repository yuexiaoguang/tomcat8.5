package org.apache.tomcat.util.descriptor.tagplugin;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.ServletContext;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.descriptor.DigesterFactory;
import org.apache.tomcat.util.descriptor.XmlErrorHandler;
import org.apache.tomcat.util.digester.Digester;
import org.apache.tomcat.util.digester.RuleSetBase;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
 * Parser for Tag Plugin descriptors.
 */
@SuppressWarnings("deprecation")
public class TagPluginParser {
    private static final Log log = LogFactory.getLog(TagPluginParser.class);
    private static final String PREFIX = "tag-plugins/tag-plugin";
    private final Digester digester;
    private final Map<String, String> plugins = new HashMap<>();

    public TagPluginParser(ServletContext context, boolean blockExternal) {
        digester = DigesterFactory.newDigester(
                false, false, new TagPluginRuleSet(), blockExternal);
        digester.setClassLoader(context.getClassLoader());
    }

    public void parse(URL url) throws IOException, SAXException {
        try (InputStream is = url.openStream()) {
            XmlErrorHandler handler = new XmlErrorHandler();
            digester.setErrorHandler(handler);

            digester.push(this);

            InputSource source = new InputSource(url.toExternalForm());
            source.setByteStream(is);
            digester.parse(source);
            if (!handler.getWarnings().isEmpty() || !handler.getErrors().isEmpty()) {
                handler.logFindings(log, source.getSystemId());
                if (!handler.getErrors().isEmpty()) {
                    // 抛出第一个表示处理过程中出错
                    throw handler.getErrors().iterator().next();
                }
            }
        } finally {
            digester.reset();
        }
    }

    public void addPlugin(String tagClass, String pluginClass) {
        plugins.put(tagClass, pluginClass);
    }

    public Map<String, String> getPlugins() {
        return plugins;
    }

    private static class TagPluginRuleSet extends RuleSetBase {
        @Override
        public void addRuleInstances(Digester digester) {
            digester.addCallMethod(PREFIX, "addPlugin", 2);
            digester.addCallParam(PREFIX + "/tag-class", 0);
            digester.addCallParam(PREFIX + "/plugin-class", 1);
        }
    }
}
