package org.apache.jasper.compiler;

import java.io.IOException;
import java.net.URL;
import java.util.Collection;
import java.util.Hashtable;
import java.util.Set;
import java.util.Vector;

import javax.servlet.ServletContext;
import javax.servlet.jsp.tagext.FunctionInfo;
import javax.servlet.jsp.tagext.TagFileInfo;
import javax.servlet.jsp.tagext.TagInfo;
import javax.servlet.jsp.tagext.TagLibraryInfo;

import org.apache.jasper.Constants;
import org.apache.jasper.JasperException;
import org.apache.jasper.JspCompilationContext;
import org.apache.tomcat.util.descriptor.tld.ImplicitTldRuleSet;
import org.apache.tomcat.util.descriptor.tld.TaglibXml;
import org.apache.tomcat.util.descriptor.tld.TldParser;
import org.apache.tomcat.util.descriptor.tld.TldResourcePath;
import org.xml.sax.SAXException;

/**
 * 负责生成"/WEB-INF/tags/"或其子目录中的标签文件对应的包含标记处理程序的隐式标记库.
 */
class ImplicitTagLibraryInfo extends TagLibraryInfo {

    private static final String WEB_INF_TAGS = "/WEB-INF/tags";
    private static final String TAG_FILE_SUFFIX = ".tag";
    private static final String TAGX_FILE_SUFFIX = ".tagx";
    private static final String TAGS_SHORTNAME = "tags";
    private static final String TLIB_VERSION = "1.0";
    private static final String JSP_VERSION = "2.0";
    private static final String IMPLICIT_TLD = "implicit.tld";

    // Maps 标签名对应标签文件路径
    private final Hashtable<String,String> tagFileMap;

    private final ParserController pc;
    private final PageInfo pi;
    private final Vector<TagFileInfo> vec;


    public ImplicitTagLibraryInfo(JspCompilationContext ctxt,
            ParserController pc,
            PageInfo pi,
            String prefix,
            String tagdir,
            ErrorDispatcher err) throws JasperException {
        super(prefix, null);
        this.pc = pc;
        this.pi = pi;
        this.tagFileMap = new Hashtable<>();
        this.vec = new Vector<>();

        // 隐式标记库没有函数:
        this.functions = new FunctionInfo[0];

        tlibversion = TLIB_VERSION;
        jspversion = JSP_VERSION;

        if (!tagdir.startsWith(WEB_INF_TAGS)) {
            err.jspError("jsp.error.invalid.tagdir", tagdir);
        }

        // 确定"imaginary" <taglib>元素的<short-name>子元素的值
        if (tagdir.equals(WEB_INF_TAGS)
                || tagdir.equals( WEB_INF_TAGS + "/")) {
            shortname = TAGS_SHORTNAME;
        } else {
            shortname = tagdir.substring(WEB_INF_TAGS.length());
            shortname = shortname.replace('/', '-');
        }

        // 填充标记名称到标记文件路径的映射
        Set<String> dirList = ctxt.getResourcePaths(tagdir);
        if (dirList != null) {
            for (String path : dirList) {
                if (path.endsWith(TAG_FILE_SUFFIX)
                        || path.endsWith(TAGX_FILE_SUFFIX)) {
                    /*
                     * 使用标记文件的文件名, 不包括 .tag 或.tagx 扩展名, 分别地, 作为"imaginary" <tag-file>的<name>子元素
                     */
                    String suffix = path.endsWith(TAG_FILE_SUFFIX) ?
                            TAG_FILE_SUFFIX : TAGX_FILE_SUFFIX;
                    String tagName = path.substring(path.lastIndexOf('/') + 1);
                    tagName = tagName.substring(0,
                            tagName.lastIndexOf(suffix));
                    tagFileMap.put(tagName, path);
                } else if (path.endsWith(IMPLICIT_TLD)) {
                    TaglibXml taglibXml;
                    try {
                        URL url = ctxt.getResource(path);
                        TldResourcePath resourcePath = new TldResourcePath(url, path);
                        ServletContext servletContext = ctxt.getServletContext();
                        boolean validate = Boolean.parseBoolean(
                                servletContext.getInitParameter(
                                        Constants.XML_VALIDATION_TLD_INIT_PARAM));
                        String blockExternalString = servletContext.getInitParameter(
                                Constants.XML_BLOCK_EXTERNAL_INIT_PARAM);
                        boolean blockExternal;
                        if (blockExternalString == null) {
                            blockExternal = true;
                        } else {
                            blockExternal = Boolean.parseBoolean(blockExternalString);
                        }
                        TldParser parser = new TldParser(true, validate,
                                new ImplicitTldRuleSet(), blockExternal);
                        taglibXml = parser.parse(resourcePath);
                    } catch (IOException | SAXException e) {
                        err.jspError(e);
                        // unreached
                        throw new JasperException(e);
                    }
                    this.tlibversion = taglibXml.getTlibVersion();
                    this.jspversion = taglibXml.getJspVersion();
                    try {
                        double version = Double.parseDouble(this.jspversion);
                        if (version < 2.0) {
                            err.jspError("jsp.error.invalid.implicit.version", path);
                        }
                    } catch (NumberFormatException e) {
                        err.jspError("jsp.error.invalid.implicit.version", path);
                    }

                    // 向依赖列表添加隐式TLD
                    if (pi != null) {
                        pi.addDependant(path, ctxt.getLastModified(path));
                    }
                }
            }
        }
    }

    /**
     * 检查给定的标签名是否映射到标签文件的路径, 如果是的话, 解析相应的标签文件.
     *
     * @return 对应于给定标签名的TagFileInfo, 或者 null 如果给定的标签名没有作为标签文件实现
     */
    @Override
    public TagFileInfo getTagFile(String shortName) {

        TagFileInfo tagFile = super.getTagFile(shortName);
        if (tagFile == null) {
            String path = tagFileMap.get(shortName);
            if (path == null) {
                return null;
            }

            TagInfo tagInfo = null;
            try {
                tagInfo = TagFileProcessor.parseTagFileDirectives(pc,
                        shortName,
                        path,
                        null,
                        this);
            } catch (JasperException je) {
                throw new RuntimeException(je.toString(), je);
            }

            tagFile = new TagFileInfo(shortName, path, tagInfo);
            vec.addElement(tagFile);

            this.tagFiles = new TagFileInfo[vec.size()];
            vec.copyInto(this.tagFiles);
        }

        return tagFile;
    }

    @Override
    public TagLibraryInfo[] getTagLibraryInfos() {
        Collection<TagLibraryInfo> coll = pi.getTaglibs();
        return coll.toArray(new TagLibraryInfo[0]);
    }
}
