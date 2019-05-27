package org.apache.tomcat.util.descriptor.tld;

import java.util.ArrayList;
import java.util.List;

import javax.servlet.jsp.tagext.FunctionInfo;

/**
 * 标记库描述符(TLD) XML文件的通用表示.
 * <p>
 * 这将存储解析TLD XML文件的原始结果, 将不同版本的描述符展平为通用格式.
 * 这与将传递给标记验证程序的TagLibraryInfo实例不同, 因为它不包含JSP用于引用此标记库的uri和前缀值.
 */
public class TaglibXml {
    private String tlibVersion;
    private String jspVersion;
    private String shortName;
    private String uri;
    private String info;
    private ValidatorXml validator;
    private final List<TagXml> tags = new ArrayList<>();
    private final List<TagFileXml> tagFiles = new ArrayList<>();
    private final List<String> listeners = new ArrayList<>();
    private final List<FunctionInfo> functions = new ArrayList<>();

    public String getTlibVersion() {
        return tlibVersion;
    }

    public void setTlibVersion(String tlibVersion) {
        this.tlibVersion = tlibVersion;
    }

    public String getJspVersion() {
        return jspVersion;
    }

    public void setJspVersion(String jspVersion) {
        this.jspVersion = jspVersion;
    }

    public String getShortName() {
        return shortName;
    }

    public void setShortName(String shortName) {
        this.shortName = shortName;
    }

    public String getUri() {
        return uri;
    }

    public void setUri(String uri) {
        this.uri = uri;
    }

    public String getInfo() {
        return info;
    }

    public void setInfo(String info) {
        this.info = info;
    }

    public ValidatorXml getValidator() {
        return validator;
    }

    public void setValidator(ValidatorXml validator) {
        this.validator = validator;
    }

    public void addTag(TagXml tag) {
        tags.add(tag);
    }

    public List<TagXml> getTags() {
        return tags;
    }

    public void addTagFile(TagFileXml tag) {
        tagFiles.add(tag);
    }

    public List<TagFileXml> getTagFiles() {
        return tagFiles;
    }

    public void addListener(String listener) {
        listeners.add(listener);
    }

    public List<String> getListeners() {
        return listeners;
    }

    public void addFunction(String name, String klass, String signature) {
        functions.add(new FunctionInfo(name, klass, signature));
    }

    public List<FunctionInfo> getFunctions() {
        return functions;
    }
}
