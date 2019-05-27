package org.apache.catalina.core;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;

import javax.servlet.http.Part;

import org.apache.tomcat.util.http.fileupload.FileItem;
import org.apache.tomcat.util.http.fileupload.ParameterParser;
import org.apache.tomcat.util.http.fileupload.disk.DiskFileItem;
import org.apache.tomcat.util.http.parser.HttpParser;

/**
 * 适配器，允许由包生成的 {@link FileItem}对象重命名Servlet 3.0 upload API 使用的commons-upload.
 */
public class ApplicationPart implements Part {

    private final FileItem fileItem;
    private final File location;

    public ApplicationPart(FileItem fileItem, File location) {
        this.fileItem = fileItem;
        this.location = location;
    }

    @Override
    public void delete() throws IOException {
        fileItem.delete();
    }

    @Override
    public String getContentType() {
        return fileItem.getContentType();
    }

    @Override
    public String getHeader(String name) {
        if (fileItem instanceof DiskFileItem) {
            return ((DiskFileItem) fileItem).getHeaders().getHeader(name);
        }
        return null;
    }

    @Override
    public Collection<String> getHeaderNames() {
        if (fileItem instanceof DiskFileItem) {
            LinkedHashSet<String> headerNames = new LinkedHashSet<>();
            Iterator<String> iter =
                ((DiskFileItem) fileItem).getHeaders().getHeaderNames();
            while (iter.hasNext()) {
                headerNames.add(iter.next());
            }
            return headerNames;
        }
        return Collections.emptyList();
    }

    @Override
    public Collection<String> getHeaders(String name) {
        if (fileItem instanceof DiskFileItem) {
            LinkedHashSet<String> headers = new LinkedHashSet<>();
            Iterator<String> iter =
                ((DiskFileItem) fileItem).getHeaders().getHeaders(name);
            while (iter.hasNext()) {
                headers.add(iter.next());
            }
            return headers;
        }
        return Collections.emptyList();
    }

    @Override
    public InputStream getInputStream() throws IOException {
        return fileItem.getInputStream();
    }

    @Override
    public String getName() {
        return fileItem.getFieldName();
    }

    @Override
    public long getSize() {
        return fileItem.getSize();
    }

    @Override
    public void write(String fileName) throws IOException {
        File file = new File(fileName);
        if (!file.isAbsolute()) {
            file = new File(location, fileName);
        }
        try {
            fileItem.write(file);
        } catch (Exception e) {
            throw new IOException(e);
        }
    }

    public String getString(String encoding) throws UnsupportedEncodingException {
        return fileItem.getString(encoding);
    }

    /*
     * 适配FileUploadBase.getFileName()
     */
    @Override
    public String getSubmittedFileName() {
        String fileName = null;
        String cd = getHeader("Content-Disposition");
        if (cd != null) {
            String cdl = cd.toLowerCase(Locale.ENGLISH);
            if (cdl.startsWith("form-data") || cdl.startsWith("attachment")) {
                ParameterParser paramParser = new ParameterParser();
                paramParser.setLowerCaseNames(true);
                // 参数解析器可以处理null输入
                Map<String,String> params = paramParser.parse(cd, ';');
                if (params.containsKey("filename")) {
                    fileName = params.get("filename");
                    // 解析器将删除周围的 '"' 但不会删除 \x .
                    if (fileName != null) {
                        // RFC 6266. 这是令牌或引号字符串
                        if (fileName.indexOf('\\') > -1) {
                            // 这是引号串
                            fileName = HttpParser.unquote(fileName.trim());
                        } else {
                            // This is a token
                            fileName = fileName.trim();
                        }
                    } else {
                        // 即使没有值，参数也存在，所以返回一个空文件名而不是文件名.
                        fileName = "";
                    }
                }
            }
        }
        return fileName;
    }
}
