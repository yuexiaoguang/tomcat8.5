package javax.servlet;

import javax.servlet.annotation.MultipartConfig;

public class MultipartConfigElement {

    private final String location;// = "";
    private final long maxFileSize;// = -1;
    private final long maxRequestSize;// = -1;
    private final int fileSizeThreshold;// = 0;

    public MultipartConfigElement(String location) {
        // 如果位置为null，则保持空字符串默认值
        if (location != null) {
            this.location = location;
        } else {
            this.location = "";
        }
        this.maxFileSize = -1;
        this.maxRequestSize = -1;
        this.fileSizeThreshold = 0;
    }

    public MultipartConfigElement(String location, long maxFileSize,
            long maxRequestSize, int fileSizeThreshold) {
        // 如果位置为null，则保持空字符串默认值
        if (location != null) {
            this.location = location;
        } else {
            this.location = "";
        }
        this.maxFileSize = maxFileSize;
        this.maxRequestSize = maxRequestSize;
        // 避免小于零，在没有数据的字段的Commons FileUpload端口中会触发 NPE.
        if (fileSizeThreshold > 0) {
            this.fileSizeThreshold = fileSizeThreshold;
        } else {
            this.fileSizeThreshold = 0;
        }
    }

    public MultipartConfigElement(MultipartConfig annotation) {
        location = annotation.location();
        maxFileSize = annotation.maxFileSize();
        maxRequestSize = annotation.maxRequestSize();
        fileSizeThreshold = annotation.fileSizeThreshold();
    }

    public String getLocation() {
        return location;
    }

    public long getMaxFileSize() {
        return maxFileSize;
    }

    public long getMaxRequestSize() {
        return maxRequestSize;
    }

    public int getFileSizeThreshold() {
        return fileSizeThreshold;
    }
}