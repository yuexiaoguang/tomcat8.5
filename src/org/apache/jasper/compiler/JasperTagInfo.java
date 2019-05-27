package org.apache.jasper.compiler;

import javax.servlet.jsp.tagext.TagAttributeInfo;
import javax.servlet.jsp.tagext.TagExtraInfo;
import javax.servlet.jsp.tagext.TagInfo;
import javax.servlet.jsp.tagext.TagLibraryInfo;
import javax.servlet.jsp.tagext.TagVariableInfo;

/**
 * 标记处理程序使用的TagInfo 扩展名, 是通过标签文件实现的.
 * 该类提供Map名称的访问, 用于存储动态属性名和传递给自定义操作调用的值.
 * 代码生成器使用此信息.
 */
class JasperTagInfo extends TagInfo {

    private final String dynamicAttrsMapName;

    public JasperTagInfo(String tagName,
            String tagClassName,
            String bodyContent,
            String infoString,
            TagLibraryInfo taglib,
            TagExtraInfo tagExtraInfo,
            TagAttributeInfo[] attributeInfo,
            String displayName,
            String smallIcon,
            String largeIcon,
            TagVariableInfo[] tvi,
            String mapName) {

        super(tagName, tagClassName, bodyContent, infoString, taglib,
                tagExtraInfo, attributeInfo, displayName, smallIcon, largeIcon,
                tvi);

        this.dynamicAttrsMapName = mapName;
    }

    public String getDynamicAttributesMapName() {
        return dynamicAttrsMapName;
    }

    @Override
    public boolean hasDynamicAttributes() {
        return dynamicAttrsMapName != null;
    }
}
