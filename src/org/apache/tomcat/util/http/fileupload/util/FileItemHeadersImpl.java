package org.apache.tomcat.util.http.fileupload.util;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.tomcat.util.http.fileupload.FileItemHeaders;


/**
 * {@link FileItemHeaders}接口的默认实现.
 */
public class FileItemHeadersImpl implements FileItemHeaders, Serializable {

    private static final long serialVersionUID = -4455695752627032559L;

    private final Map<String,List<String>> headerNameToValueListMap =
            new LinkedHashMap<>();

    /**
     * {@inheritDoc}
     */
    @Override
    public String getHeader(String name) {
        String nameLower = name.toLowerCase(Locale.ENGLISH);
        List<String> headerValueList = headerNameToValueListMap.get(nameLower);
        if (null == headerValueList) {
            return null;
        }
        return headerValueList.get(0);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Iterator<String> getHeaderNames() {
        return headerNameToValueListMap.keySet().iterator();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Iterator<String> getHeaders(String name) {
        String nameLower = name.toLowerCase(Locale.ENGLISH);
        List<String> headerValueList = headerNameToValueListMap.get(nameLower);
        if (null == headerValueList) {
            headerValueList = Collections.emptyList();
        }
        return headerValueList.iterator();
    }

    /**
     * 将header值添加到此实例的方法.
     *
     * @param name header的名称
     * @param value header的值
     */
    public synchronized void addHeader(String name, String value) {
        String nameLower = name.toLowerCase(Locale.ENGLISH);
        List<String> headerValueList = headerNameToValueListMap.get(nameLower);
        if (null == headerValueList) {
            headerValueList = new ArrayList<>();
            headerNameToValueListMap.put(nameLower, headerValueList);
        }
        headerValueList.add(value);
    }

}
