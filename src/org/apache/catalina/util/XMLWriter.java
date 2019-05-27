package org.apache.catalina.util;

import java.io.IOException;
import java.io.Writer;

/**
 * XMLWriter helper class.
 */
public class XMLWriter {


    // -------------------------------------------------------------- Constants


    /**
     * 开放标签.
     */
    public static final int OPENING = 0;


    /**
     * 关闭标签.
     */
    public static final int CLOSING = 1;


    /**
     * 无内容元素.
     */
    public static final int NO_CONTENT = 2;


    // ----------------------------------------------------- Instance Variables


    /**
     * Buffer.
     */
    protected StringBuilder buffer = new StringBuilder();


    /**
     * Writer.
     */
    protected final Writer writer;


    // ----------------------------------------------------------- Constructors


    public XMLWriter() {
        this(null);
    }


    /**
     * @param writer The writer to use
     */
    public XMLWriter(Writer writer) {
        this.writer = writer;
    }


    // --------------------------------------------------------- Public Methods


    @Override
    public String toString() {
        return buffer.toString();
    }


    /**
     * 写入属性到 XML.
     *
     * @param namespace Namespace
     * @param name Property name
     * @param value Property value
     */
    public void writeProperty(String namespace, String name, String value) {
        writeElement(namespace, name, OPENING);
        buffer.append(value);
        writeElement(namespace, name, CLOSING);
    }


    /**
     * 写入一个元素.
     *
     * @param namespace 命名空间缩写
     * @param name Element name
     * @param type Element type
     */
    public void writeElement(String namespace, String name, int type) {
        writeElement(namespace, null, name, type);
    }


    /**
     * 写入一个元素.
     *
     * @param namespace 命名空间缩写
     * @param namespaceInfo 命名空间信息
     * @param name Element name
     * @param type Element type
     */
    public void writeElement(String namespace, String namespaceInfo,
                             String name, int type) {
        if ((namespace != null) && (namespace.length() > 0)) {
            switch (type) {
            case OPENING:
                if (namespaceInfo != null) {
                    buffer.append("<" + namespace + ":" + name + " xmlns:"
                                  + namespace + "=\""
                                  + namespaceInfo + "\">");
                } else {
                    buffer.append("<" + namespace + ":" + name + ">");
                }
                break;
            case CLOSING:
                buffer.append("</" + namespace + ":" + name + ">\n");
                break;
            case NO_CONTENT:
            default:
                if (namespaceInfo != null) {
                    buffer.append("<" + namespace + ":" + name + " xmlns:"
                                  + namespace + "=\""
                                  + namespaceInfo + "\"/>");
                } else {
                    buffer.append("<" + namespace + ":" + name + "/>");
                }
                break;
            }
        } else {
            switch (type) {
            case OPENING:
                buffer.append("<" + name + ">");
                break;
            case CLOSING:
                buffer.append("</" + name + ">\n");
                break;
            case NO_CONTENT:
            default:
                buffer.append("<" + name + "/>");
                break;
            }
        }
    }


    /**
     * 写入文本.
     *
     * @param text 要追加的文本
     */
    public void writeText(String text) {
        buffer.append(text);
    }


    /**
     * 写入数据.
     *
     * @param data 要追加的数据
     */
    public void writeData(String data) {
        buffer.append("<![CDATA[" + data + "]]>");
    }


    /**
     * 写入XML Header.
     */
    public void writeXMLHeader() {
        buffer.append("<?xml version=\"1.0\" encoding=\"utf-8\" ?>\n");
    }


    /**
     * 发送数据并重新初始化缓冲区, 如果已经指定writer.
     * 
     * @throws IOException 写入XML数据错误
     */
    public void sendData()
        throws IOException {
        if (writer != null) {
            writer.write(buffer.toString());
            buffer = new StringBuilder();
        }
    }
}
