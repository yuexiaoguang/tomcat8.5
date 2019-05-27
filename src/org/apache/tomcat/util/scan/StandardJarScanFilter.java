package org.apache.tomcat.util.scan;

import java.util.HashSet;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.apache.tomcat.JarScanFilter;
import org.apache.tomcat.JarScanType;
import org.apache.tomcat.util.file.Matcher;

public class StandardJarScanFilter implements JarScanFilter {

    private final ReadWriteLock configurationLock =
            new ReentrantReadWriteLock();

    private static final String defaultSkip;
    private static final String defaultScan;
    private static final Set<String> defaultSkipSet = new HashSet<>();
    private static final Set<String> defaultScanSet = new HashSet<>();

    static {
        // 初始化缺省值. 没有设置它们的方法.
        defaultSkip = System.getProperty(Constants.SKIP_JARS_PROPERTY);
        populateSetFromAttribute(defaultSkip, defaultSkipSet);
        defaultScan = System.getProperty(Constants.SCAN_JARS_PROPERTY);
        populateSetFromAttribute(defaultScan, defaultScanSet);
    }

    private String tldSkip;
    private String tldScan;
    private final Set<String> tldSkipSet;
    private final Set<String> tldScanSet;
    private boolean defaultTldScan = true;

    private String pluggabilitySkip;
    private String pluggabilityScan;
    private final Set<String> pluggabilitySkipSet;
    private final Set<String> pluggabilityScanSet;
    private boolean defaultPluggabilityScan = true;

    /**
     * 这是{@link JarScanFilter}的标准实现. 默认情况下, 使用以下过滤规则:
     * <ul>
     * <li>扫描结果中既不跳过也不与扫描列表匹配的JAR.</li>
     * <li>与跳过列表匹配但不与扫描列表匹配的JAR, 将被排除在扫描结果之外.</li>
     * <li>与扫描列表匹配的JAR将包含在扫描结果中.</li>
     * </ul>
     * 默认跳过列表和默认扫描列表分别从系统属性 {@link Constants#SKIP_JARS_PROPERTY} 和 {@link Constants#SCAN_JARS_PROPERTY} 获得.
     * 这些默认值对于 {@link JarScanType#TLD} 和 {@link JarScanType#PLUGGABILITY}扫描来说可能会被重写.
     * 还可以使用 {@link #setDefaultTldScan(boolean)} 和 {@link #setDefaultPluggabilityScan(boolean)}修改这些扫描类型的过滤规则.
     * 如果设置为<code>false</code>, 以下类型的过滤规则用于关联类型:
     * <ul>
     * <li>与跳过或扫描列表不匹配的JAR, 将被排除在扫描结果之外.</li>
     * <li>与扫描列表匹配, 但不是跳过列表的JAR将包含在扫描结果中.</li>
     * <li>与跳过列表匹配的JAR将被排除在扫描结果之外</li>
     * </ul>
     */
    public StandardJarScanFilter() {
        tldSkip = defaultSkip;
        tldSkipSet = new HashSet<>(defaultSkipSet);
        tldScan = defaultScan;
        tldScanSet = new HashSet<>(defaultScanSet);
        pluggabilitySkip = defaultSkip;
        pluggabilitySkipSet = new HashSet<>(defaultSkipSet);
        pluggabilityScan = defaultScan;
        pluggabilityScanSet = new HashSet<>(defaultScanSet);
    }


    public String getTldSkip() {
        return tldSkip;
    }


    public void setTldSkip(String tldSkip) {
        this.tldSkip = tldSkip;
        Lock writeLock = configurationLock.writeLock();
        writeLock.lock();
        try {
            populateSetFromAttribute(tldSkip, tldSkipSet);
        } finally {
            writeLock.unlock();
        }
    }


    public String getTldScan() {
        return tldScan;
    }


    public void setTldScan(String tldScan) {
        this.tldScan = tldScan;
        Lock writeLock = configurationLock.writeLock();
        writeLock.lock();
        try {
            populateSetFromAttribute(tldScan, tldScanSet);
        } finally {
            writeLock.unlock();
        }
    }


    public boolean isDefaultTldScan() {
        return defaultTldScan;
    }


    public void setDefaultTldScan(boolean defaultTldScan) {
        this.defaultTldScan = defaultTldScan;
    }


    public String getPluggabilitySkip() {
        return pluggabilitySkip;
    }


    public void setPluggabilitySkip(String pluggabilitySkip) {
        this.pluggabilitySkip = pluggabilitySkip;
        Lock writeLock = configurationLock.writeLock();
        writeLock.lock();
        try {
            populateSetFromAttribute(pluggabilitySkip, pluggabilitySkipSet);
        } finally {
            writeLock.unlock();
        }
    }


    public String getPluggabilityScan() {
        return pluggabilityScan;
    }


    public void setPluggabilityScan(String pluggabilityScan) {
        this.pluggabilityScan = pluggabilityScan;
        Lock writeLock = configurationLock.writeLock();
        writeLock.lock();
        try {
            populateSetFromAttribute(pluggabilityScan, pluggabilityScanSet);
        } finally {
            writeLock.unlock();
        }
    }


    public boolean isDefaultPluggabilityScan() {
        return defaultPluggabilityScan;
    }


    public void setDefaultPluggabilityScan(boolean defaultPluggabilityScan) {
        this.defaultPluggabilityScan = defaultPluggabilityScan;
    }


    @Override
    public boolean check(JarScanType jarScanType, String jarName) {
        Lock readLock = configurationLock.readLock();
        readLock.lock();
        try {
            final boolean defaultScan;
            final Set<String> toSkip;
            final Set<String> toScan;
            switch (jarScanType) {
                case TLD: {
                    defaultScan = defaultTldScan;
                    toSkip = tldSkipSet;
                    toScan = tldScanSet;
                    break;
                }
                case PLUGGABILITY: {
                    defaultScan = defaultPluggabilityScan;
                    toSkip = pluggabilitySkipSet;
                    toScan = pluggabilityScanSet;
                    break;
                }
                case OTHER:
                default: {
                    defaultScan = true;
                    toSkip = defaultSkipSet;
                    toScan = defaultScanSet;
                }
            }
            if (defaultScan) {
                if (Matcher.matchName(toSkip, jarName)) {
                    if (Matcher.matchName(toScan, jarName)) {
                        return true;
                    } else {
                        return false;
                    }
                }
                return true;
            } else {
                if (Matcher.matchName(toScan, jarName)) {
                    if (Matcher.matchName(toSkip, jarName)) {
                        return false;
                    } else {
                        return true;
                    }
                }
                return false;
            }
        } finally {
            readLock.unlock();
        }
    }

    private static void populateSetFromAttribute(String attribute, Set<String> set) {
        set.clear();
        if (attribute != null) {
            StringTokenizer tokenizer = new StringTokenizer(attribute, ",");
            while (tokenizer.hasMoreElements()) {
                String token = tokenizer.nextToken().trim();
                if (token.length() > 0) {
                    set.add(token);
                }
            }
        }
    }
}
