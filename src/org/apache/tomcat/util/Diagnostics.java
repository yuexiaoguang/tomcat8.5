package org.apache.tomcat.util;

import java.lang.management.ClassLoadingMXBean;
import java.lang.management.CompilationMXBean;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.LockInfo;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryManagerMXBean;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryUsage;
import java.lang.management.MonitorInfo;
import java.lang.management.OperatingSystemMXBean;
import java.lang.management.PlatformLoggingMXBean;
import java.lang.management.RuntimeMXBean;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.res.StringManager;

public class Diagnostics {

    private static final String PACKAGE = "org.apache.tomcat.util";
    private static final StringManager sm = StringManager.getManager(PACKAGE);

    private static final String INDENT1 = "  ";
    private static final String INDENT2 = "\t";
    private static final String INDENT3 = "   ";
    private static final String CRLF = "\r\n";
    private static final String vminfoSystemProperty = "java.vm.info";

    private static final Log log = LogFactory.getLog(Diagnostics.class);

    private static final SimpleDateFormat timeformat =
        new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");

    /* Some platform MBeans */
    private static final ClassLoadingMXBean classLoadingMXBean =
        ManagementFactory.getClassLoadingMXBean();
    private static final CompilationMXBean compilationMXBean =
        ManagementFactory.getCompilationMXBean();
    private static final OperatingSystemMXBean operatingSystemMXBean =
        ManagementFactory.getOperatingSystemMXBean();
    private static final RuntimeMXBean runtimeMXBean =
        ManagementFactory.getRuntimeMXBean();
    private static final ThreadMXBean threadMXBean =
        ManagementFactory.getThreadMXBean();

    // XXX 不确定是否应该根据需要更好地检索以下MBean, i.e. 是否可以在MBeanServer中动态更改.
    private static final PlatformLoggingMXBean loggingMXBean =
        ManagementFactory.getPlatformMXBean(PlatformLoggingMXBean.class);
    private static final MemoryMXBean memoryMXBean =
        ManagementFactory.getMemoryMXBean();
    private static final List<GarbageCollectorMXBean> garbageCollectorMXBeans =
        ManagementFactory.getGarbageCollectorMXBeans();
    private static final List<MemoryManagerMXBean> memoryManagerMXBeans =
        ManagementFactory.getMemoryManagerMXBeans();
    private static final List<MemoryPoolMXBean> memoryPoolMXBeans =
        ManagementFactory.getMemoryPoolMXBeans();

    /**
     * 检查是否启用了线程竞争监视.
     *
     * @return true 如果启用了线程竞争监视
     */
    public static boolean isThreadContentionMonitoringEnabled() {
        return threadMXBean.isThreadContentionMonitoringEnabled();
    }

    /**
     * 通过ThreadMxMXBean启用或禁用线程竞争监视.
     *
     * @param enable 是否启用线程竞争监视
     */
    public static void setThreadContentionMonitoringEnabled(boolean enable) {
        threadMXBean.setThreadContentionMonitoringEnabled(enable);
        boolean checkValue = threadMXBean.isThreadContentionMonitoringEnabled();
        if (enable != checkValue) {
            log.error("Could not set threadContentionMonitoringEnabled to " +
                      enable + ", got " + checkValue + " instead");
        }
    }

    /**
     * 检查是否启用了线程cpu时间测量.
     *
     * @return true 如果启用了线程cpu时间测量
     */
    public static boolean isThreadCpuTimeEnabled() {
        return threadMXBean.isThreadCpuTimeEnabled();
    }

    /**
     * 通过ThreadMxMXBean启用或禁用线程CPU时间测量.
     *
     * @param enable 是否启用线程cpu时间测量
     */
    public static void setThreadCpuTimeEnabled(boolean enable) {
        threadMXBean.setThreadCpuTimeEnabled(enable);
        boolean checkValue = threadMXBean.isThreadCpuTimeEnabled();
        if (enable != checkValue) {
            log.error("Could not set threadCpuTimeEnabled to " + enable +
                      ", got " + checkValue + " instead");
        }
    }

    /**
     * 重置ThreadMXBean中的峰值线程计数
     */
    public static void resetPeakThreadCount() {
        threadMXBean.resetPeakThreadCount();
    }

    /**
     * 设置详细类加载
     *
     * @param verbose 是否启用详细类加载
     */
    public static void setVerboseClassLoading(boolean verbose) {
        classLoadingMXBean.setVerbose(verbose);
        boolean checkValue = classLoadingMXBean.isVerbose();
        if (verbose != checkValue) {
            log.error("Could not set verbose class loading to " + verbose +
                      ", got " + checkValue + " instead");
        }
    }

    /**
     * 设置 Logger 级别
     *
     * @param loggerName logger名称
     * @param levelName 要设置的级别
     */
    public static void setLoggerLevel(String loggerName, String levelName) {
        loggingMXBean.setLoggerLevel(loggerName, levelName);
        String checkValue = loggingMXBean.getLoggerLevel(loggerName);
        if (!checkValue.equals(levelName)) {
            log.error("Could not set logger level for logger '" +
                      loggerName + "' to '" + levelName +
                      "', got '" + checkValue + "' instead");
        }
    }

    /**
     * 设置详细垃圾回收日志记录
     *
     * @param verbose 是否启用详细的gc日志记录
     */
    public static void setVerboseGarbageCollection(boolean verbose) {
        memoryMXBean.setVerbose(verbose);
        boolean checkValue = memoryMXBean.isVerbose();
        if (verbose != checkValue) {
            log.error("Could not set verbose garbage collection logging to " + verbose +
                      ", got " + checkValue + " instead");
        }
    }

    /**
     * 通过MX Bean启动垃圾回收
     */
    public static void gc() {
        memoryMXBean.gc();
    }

    /**
     * 重置MemoryPoolMXBean中的峰值内存使用情况数据
     *
     * @param name MemoryPoolMXBean的名称或"all"
     */
    public static void resetPeakUsage(String name) {
        for (MemoryPoolMXBean mbean: memoryPoolMXBeans) {
            if (name.equals("all") || name.equals(mbean.getName())) {
                mbean.resetPeakUsage();
            }
        }
    }

    /**
     * 设置 MemoryPoolMXBean 中使用率阈值
     *
     * @param name MemoryPoolMXBean名称
     * @param threshold 设置的阈值
     * 
     * @return true 如果设置阈值成功
     */
    public static boolean setUsageThreshold(String name, long threshold) {
        for (MemoryPoolMXBean mbean: memoryPoolMXBeans) {
            if (name.equals(mbean.getName())) {
                try {
                    mbean.setUsageThreshold(threshold);
                    return true;
                } catch (IllegalArgumentException ex) {
                    // IGNORE
                } catch (UnsupportedOperationException ex) {
                    // IGNORE
                }
                return false;
            }
        }
        return false;
    }

    /**
     * 设置MemoryPoolMXBean中集合使用阈值
     *
     * @param name MemoryPoolMXBean名称
     * @param threshold 要设置的集合阈值
     * 
     * @return true 如果设置阈值成功
     */
    public static boolean setCollectionUsageThreshold(String name, long threshold) {
        for (MemoryPoolMXBean mbean: memoryPoolMXBeans) {
            if (name.equals(mbean.getName())) {
                try {
                    mbean.setCollectionUsageThreshold(threshold);
                    return true;
                } catch (IllegalArgumentException ex) {
                    // IGNORE
                } catch (UnsupportedOperationException ex) {
                    // IGNORE
                }
                return false;
            }
        }
        return false;
    }

    /**
     * 格式化一个线程的线程转储报头.
     *
     * @param ti 描述线程的ThreadInfo
     * 
     * @return 格式化的线程转储报头
     */
    private static String getThreadDumpHeader(ThreadInfo ti) {
        StringBuilder sb = new StringBuilder("\"" + ti.getThreadName() + "\"");
        sb.append(" Id=" + ti.getThreadId());
        sb.append(" cpu=" + threadMXBean.getThreadCpuTime(ti.getThreadId()) +
                  " ns");
        sb.append(" usr=" + threadMXBean.getThreadUserTime(ti.getThreadId()) +
                  " ns");
        sb.append(" blocked " + ti.getBlockedCount() + " for " +
                  ti.getBlockedTime() + " ms");
        sb.append(" waited " + ti.getWaitedCount() + " for " +
                  ti.getWaitedTime() + " ms");

        if (ti.isSuspended()) {
            sb.append(" (suspended)");
        }
        if (ti.isInNative()) {
            sb.append(" (running in native)");
        }
        sb.append(CRLF);
        sb.append(INDENT3 + "java.lang.Thread.State: " + ti.getThreadState());
        sb.append(CRLF);
        return sb.toString();
    }

    /**
     * 格式化一个线程的线程转储.
     *
     * @param ti 描述线程的ThreadInfo
     * 
     * @return 格式化的线程转储
     */
    private static String getThreadDump(ThreadInfo ti) {
        StringBuilder sb = new StringBuilder(getThreadDumpHeader(ti));
        for (LockInfo li : ti.getLockedSynchronizers()) {
            sb.append(INDENT2 + "locks " +
                      li.toString() + CRLF);
        }
        boolean start = true;
        StackTraceElement[] stes = ti.getStackTrace();
        Object[] monitorDepths = new Object[stes.length];
        MonitorInfo[] mis = ti.getLockedMonitors();
        for (int i = 0; i < mis.length; i++) {
            monitorDepths[mis[i].getLockedStackDepth()] = mis[i];
        }
        for (int i = 0; i < stes.length; i++) {
            StackTraceElement ste = stes[i];
            sb.append(INDENT2 +
                      "at " + ste.toString() + CRLF);
            if (start) {
                if (ti.getLockName() != null) {
                    sb.append(INDENT2 + "- waiting on (a " +
                              ti.getLockName() + ")");
                    if (ti.getLockOwnerName() != null) {
                        sb.append(" owned by " + ti.getLockOwnerName() +
                                  " Id=" + ti.getLockOwnerId());
                    }
                    sb.append(CRLF);
                }
                start = false;
            }
            if (monitorDepths[i] != null) {
                MonitorInfo mi = (MonitorInfo)monitorDepths[i];
                sb.append(INDENT2 +
                          "- locked (a " + mi.toString() + ")"+
                          " index " + mi.getLockedStackDepth() +
                          " frame " + mi.getLockedStackFrame().toString());
                sb.append(CRLF);

            }
        }
        return sb.toString();
    }

    /**
     * 格式化线程列表的线程转储.
     *
     * @param tinfos 描述线程列表的ThreadInfo数组
     * @return 格式化的线程转储
     */
    private static String getThreadDump(ThreadInfo[] tinfos) {
        StringBuilder sb = new StringBuilder();
        for (ThreadInfo tinfo : tinfos) {
            sb.append(getThreadDump(tinfo));
            sb.append(CRLF);
        }
        return sb.toString();
    }

    /**
     * 检查是否有线程死锁. 如果有, 打印那些线程的线程转储.
     *
     * @return 死锁消息和死锁线程的格式化线程转储
     */
    public static String findDeadlock() {
        ThreadInfo[] tinfos = null;
        long[] ids = threadMXBean.findDeadlockedThreads();
        if (ids != null) {
            tinfos = threadMXBean.getThreadInfo(threadMXBean.findDeadlockedThreads(),
                                                true, true);
            if (tinfos != null) {
                StringBuilder sb =
                    new StringBuilder("Deadlock found between the following threads:");
                sb.append(CRLF);
                sb.append(getThreadDump(tinfos));
                return sb.toString();
            }
        }
        return "";
    }

    /**
     * 检索格式化的JVM线程转储. 将使用默认的StringManager.
     *
     * @return 格式化的JVM线程转储
     */
    public static String getThreadDump() {
        return getThreadDump(sm);
    }

    /**
     * 检索格式化的JVM线程转储. 给定的区域列表将用于检索StringManager.
     *
     * @param requestedLocales 要使用的区域列表
     * 
     * @return 格式化的JVM线程转储
     */
    public static String getThreadDump(Enumeration<Locale> requestedLocales) {
        return getThreadDump(
                StringManager.getManager(PACKAGE, requestedLocales));
    }

    /**
     * 检索使用给定StringManager格式化的JVM线程转储.
     *
     * @param requestedSm 要使用的StringManager
     * 
     * @return 格式化的JVM线程转储
     */
    public static String getThreadDump(StringManager requestedSm) {
        StringBuilder sb = new StringBuilder();

        synchronized(timeformat) {
            sb.append(timeformat.format(new Date()));
        }
        sb.append(CRLF);

        sb.append(requestedSm.getString("diagnostics.threadDumpTitle"));
        sb.append(" ");
        sb.append(runtimeMXBean.getVmName());
        sb.append(" (");
        sb.append(runtimeMXBean.getVmVersion());
        String vminfo = System.getProperty(vminfoSystemProperty);
        if (vminfo != null) {
            sb.append(" " + vminfo);
        }
        sb.append("):" + CRLF);
        sb.append(CRLF);

        ThreadInfo[] tis = threadMXBean.dumpAllThreads(true, true);
        sb.append(getThreadDump(tis));

        sb.append(findDeadlock());
        return sb.toString();
    }

    /**
     * 格式化MemoryUsage对象的内容.
     * 
     * @param name 格式化中使用的文本前缀
     * @param usage 要格式化的MemoryUsage对象
     * 
     * @return 格式化的内容
     */
    private static String formatMemoryUsage(String name, MemoryUsage usage) {
        if (usage != null) {
            StringBuilder sb = new StringBuilder();
            sb.append(INDENT1 + name + " init: " + usage.getInit() + CRLF);
            sb.append(INDENT1 + name + " used: " + usage.getUsed() + CRLF);
            sb.append(INDENT1 + name + " committed: " + usage.getCommitted() + CRLF);
            sb.append(INDENT1 + name + " max: " + usage.getMax() + CRLF);
            return sb.toString();
        }
        return "";
    }

    /**
     * 检索格式化的JVM信息文本. 将使用默认的StringManager.
     *
     * @return 格式化的JVM信息文本
     */
    public static String getVMInfo() {
        return getVMInfo(sm);
    }

    /**
     * 检索格式化的JVM信息文本. 将用于检索StringManager的区域列表.
     *
     * @param requestedLocales 要使用的区域列表
     * 
     * @return 格式化的JVM信息文本
     */
    public static String getVMInfo(Enumeration<Locale> requestedLocales) {
        return getVMInfo(StringManager.getManager(PACKAGE, requestedLocales));
    }

    /**
     * 检索使用给定StringManager格式化的JVM信息文本.
     *
     * @param requestedSm 要使用的StringManager
     * 
     * @return 格式化的JVM信息文本
     */
    public static String getVMInfo(StringManager requestedSm) {
        StringBuilder sb = new StringBuilder();

        synchronized(timeformat) {
            sb.append(timeformat.format(new Date()));
        }
        sb.append(CRLF);

        sb.append(requestedSm.getString("diagnostics.vmInfoRuntime"));
        sb.append(":" + CRLF);
        sb.append(INDENT1 + "vmName: " + runtimeMXBean.getVmName() + CRLF);
        sb.append(INDENT1 + "vmVersion: " + runtimeMXBean.getVmVersion() + CRLF);
        sb.append(INDENT1 + "vmVendor: " + runtimeMXBean.getVmVendor() + CRLF);
        sb.append(INDENT1 + "specName: " + runtimeMXBean.getSpecName() + CRLF);
        sb.append(INDENT1 + "specVersion: " + runtimeMXBean.getSpecVersion() + CRLF);
        sb.append(INDENT1 + "specVendor: " + runtimeMXBean.getSpecVendor() + CRLF);
        sb.append(INDENT1 + "managementSpecVersion: " +
                  runtimeMXBean.getManagementSpecVersion() + CRLF);
        sb.append(INDENT1 + "name: " + runtimeMXBean.getName() + CRLF);
        sb.append(INDENT1 + "startTime: " + runtimeMXBean.getStartTime() + CRLF);
        sb.append(INDENT1 + "uptime: " + runtimeMXBean.getUptime() + CRLF);
        sb.append(INDENT1 + "isBootClassPathSupported: " +
                  runtimeMXBean.isBootClassPathSupported() + CRLF);
        sb.append(CRLF);

        sb.append(requestedSm.getString("diagnostics.vmInfoOs"));
        sb.append(":" + CRLF);
        sb.append(INDENT1 + "name: " + operatingSystemMXBean.getName() + CRLF);
        sb.append(INDENT1 + "version: " + operatingSystemMXBean.getVersion() + CRLF);
        sb.append(INDENT1 + "architecture: " + operatingSystemMXBean.getArch() + CRLF);
        sb.append(INDENT1 + "availableProcessors: " +
                  operatingSystemMXBean.getAvailableProcessors() + CRLF);
        sb.append(INDENT1 + "systemLoadAverage: " +
                  operatingSystemMXBean.getSystemLoadAverage() + CRLF);
        sb.append(CRLF);

        sb.append(requestedSm.getString("diagnostics.vmInfoThreadMxBean"));
        sb.append(":" + CRLF);
        sb.append(INDENT1 + "isCurrentThreadCpuTimeSupported: " +
                  threadMXBean.isCurrentThreadCpuTimeSupported() + CRLF);
        sb.append(INDENT1 + "isThreadCpuTimeSupported: " +
                  threadMXBean.isThreadCpuTimeSupported() + CRLF);
        sb.append(INDENT1 + "isThreadCpuTimeEnabled: " +
                  threadMXBean.isThreadCpuTimeEnabled() + CRLF);
        sb.append(INDENT1 + "isObjectMonitorUsageSupported: " +
                  threadMXBean.isObjectMonitorUsageSupported() + CRLF);
        sb.append(INDENT1 + "isSynchronizerUsageSupported: " +
                  threadMXBean.isSynchronizerUsageSupported() + CRLF);
        sb.append(INDENT1 + "isThreadContentionMonitoringSupported: " +
                  threadMXBean.isThreadContentionMonitoringSupported() + CRLF);
        sb.append(INDENT1 + "isThreadContentionMonitoringEnabled: " +
                  threadMXBean.isThreadContentionMonitoringEnabled() + CRLF);
        sb.append(CRLF);

        sb.append(requestedSm.getString("diagnostics.vmInfoThreadCounts"));
        sb.append(":" + CRLF);
        sb.append(INDENT1 + "daemon: " + threadMXBean.getDaemonThreadCount() + CRLF);
        sb.append(INDENT1 + "total: " + threadMXBean.getThreadCount() + CRLF);
        sb.append(INDENT1 + "peak: " + threadMXBean.getPeakThreadCount() + CRLF);
        sb.append(INDENT1 + "totalStarted: " +
                  threadMXBean.getTotalStartedThreadCount() + CRLF);
        sb.append(CRLF);

        sb.append(requestedSm.getString("diagnostics.vmInfoStartup"));
        sb.append(":" + CRLF);
        for (String arg: runtimeMXBean.getInputArguments()) {
            sb.append(INDENT1 + arg + CRLF);
        }
        sb.append(CRLF);

        sb.append(requestedSm.getString("diagnostics.vmInfoPath"));
        sb.append(":" + CRLF);
        sb.append(INDENT1 + "bootClassPath: " + runtimeMXBean.getBootClassPath() + CRLF);
        sb.append(INDENT1 + "classPath: " + runtimeMXBean.getClassPath() + CRLF);
        sb.append(INDENT1 + "libraryPath: " + runtimeMXBean.getLibraryPath() + CRLF);
        sb.append(CRLF);

        sb.append(requestedSm.getString("diagnostics.vmInfoClassLoading"));
        sb.append(":" + CRLF);
        sb.append(INDENT1 + "loaded: " +
                  classLoadingMXBean.getLoadedClassCount() + CRLF);
        sb.append(INDENT1 + "unloaded: " +
                  classLoadingMXBean.getUnloadedClassCount() + CRLF);
        sb.append(INDENT1 + "totalLoaded: " +
                  classLoadingMXBean.getTotalLoadedClassCount() + CRLF);
        sb.append(INDENT1 + "isVerbose: " +
                  classLoadingMXBean.isVerbose() + CRLF);
        sb.append(CRLF);

        sb.append(requestedSm.getString("diagnostics.vmInfoClassCompilation"));
        sb.append(":" + CRLF);
        sb.append(INDENT1 + "name: " + compilationMXBean.getName() + CRLF);
        sb.append(INDENT1 + "totalCompilationTime: " +
                  compilationMXBean.getTotalCompilationTime() + CRLF);
        sb.append(INDENT1 + "isCompilationTimeMonitoringSupported: " +
                  compilationMXBean.isCompilationTimeMonitoringSupported() + CRLF);
        sb.append(CRLF);

        for (MemoryManagerMXBean mbean: memoryManagerMXBeans) {
            sb.append(requestedSm.getString("diagnostics.vmInfoMemoryManagers", mbean.getName()));
            sb.append(":" + CRLF);
            sb.append(INDENT1 + "isValid: " + mbean.isValid() + CRLF);
            sb.append(INDENT1 + "mbean.getMemoryPoolNames: " + CRLF);
            String[] names = mbean.getMemoryPoolNames();
            Arrays.sort(names);
            for (String name: names) {
                sb.append(INDENT2 + name + CRLF);
            }
            sb.append(CRLF);
        }

        for (GarbageCollectorMXBean mbean: garbageCollectorMXBeans) {
            sb.append(requestedSm.getString("diagnostics.vmInfoGarbageCollectors", mbean.getName()));
            sb.append(":" + CRLF);
            sb.append(INDENT1 + "isValid: " + mbean.isValid() + CRLF);
            sb.append(INDENT1 + "mbean.getMemoryPoolNames: " + CRLF);
            String[] names = mbean.getMemoryPoolNames();
            Arrays.sort(names);
            for (String name: names) {
                sb.append(INDENT2 + name + CRLF);
            }
            sb.append(INDENT1 + "getCollectionCount: " + mbean.getCollectionCount() + CRLF);
            sb.append(INDENT1 + "getCollectionTime: " + mbean.getCollectionTime() + CRLF);
            sb.append(CRLF);
        }

        sb.append(requestedSm.getString("diagnostics.vmInfoMemory"));
        sb.append(":" + CRLF);
        sb.append(INDENT1 + "isVerbose: " + memoryMXBean.isVerbose() + CRLF);
        sb.append(INDENT1 + "getObjectPendingFinalizationCount: " + memoryMXBean.getObjectPendingFinalizationCount() + CRLF);
        sb.append(formatMemoryUsage("heap", memoryMXBean.getHeapMemoryUsage()));
        sb.append(formatMemoryUsage("non-heap", memoryMXBean.getNonHeapMemoryUsage()));
        sb.append(CRLF);

        for (MemoryPoolMXBean mbean: memoryPoolMXBeans) {
            sb.append(requestedSm.getString("diagnostics.vmInfoMemoryPools", mbean.getName()));
            sb.append(":" + CRLF);
            sb.append(INDENT1 + "isValid: " + mbean.isValid() + CRLF);
            sb.append(INDENT1 + "getType: " + mbean.getType() + CRLF);
            sb.append(INDENT1 + "mbean.getMemoryManagerNames: " + CRLF);
            String[] names = mbean.getMemoryManagerNames();
            Arrays.sort(names);
            for (String name: names) {
                sb.append(INDENT2 + name + CRLF);
            }
            sb.append(INDENT1 + "isUsageThresholdSupported: " + mbean.isUsageThresholdSupported() + CRLF);
            try {
                sb.append(INDENT1 + "isUsageThresholdExceeded: " + mbean.isUsageThresholdExceeded() + CRLF);
            } catch (UnsupportedOperationException ex) {
                // IGNORE
            }
            sb.append(INDENT1 + "isCollectionUsageThresholdSupported: " + mbean.isCollectionUsageThresholdSupported() + CRLF);
            try {
                sb.append(INDENT1 + "isCollectionUsageThresholdExceeded: " + mbean.isCollectionUsageThresholdExceeded() + CRLF);
            } catch (UnsupportedOperationException ex) {
                // IGNORE
            }
            try {
                sb.append(INDENT1 + "getUsageThreshold: " + mbean.getUsageThreshold() + CRLF);
            } catch (UnsupportedOperationException ex) {
                // IGNORE
            }
            try {
                sb.append(INDENT1 + "getUsageThresholdCount: " + mbean.getUsageThresholdCount() + CRLF);
            } catch (UnsupportedOperationException ex) {
                // IGNORE
            }
            try {
                sb.append(INDENT1 + "getCollectionUsageThreshold: " + mbean.getCollectionUsageThreshold() + CRLF);
            } catch (UnsupportedOperationException ex) {
                // IGNORE
            }
            try {
                sb.append(INDENT1 + "getCollectionUsageThresholdCount: " + mbean.getCollectionUsageThresholdCount() + CRLF);
            } catch (UnsupportedOperationException ex) {
                // IGNORE
            }
            sb.append(formatMemoryUsage("current", mbean.getUsage()));
            sb.append(formatMemoryUsage("collection", mbean.getCollectionUsage()));
            sb.append(formatMemoryUsage("peak", mbean.getPeakUsage()));
            sb.append(CRLF);
        }


        sb.append(requestedSm.getString("diagnostics.vmInfoSystem"));
        sb.append(":" + CRLF);
        Map<String,String> props = runtimeMXBean.getSystemProperties();
        ArrayList<String> keys = new ArrayList<>(props.keySet());
        Collections.sort(keys);
        for (String prop: keys) {
            sb.append(INDENT1 + prop + ": " + props.get(prop) + CRLF);
        }
        sb.append(CRLF);

        sb.append(requestedSm.getString("diagnostics.vmInfoLogger"));
        sb.append(":" + CRLF);
        List<String> loggers = loggingMXBean.getLoggerNames();
        Collections.sort(loggers);
        for (String logger: loggers) {
            sb.append(INDENT1 + logger +
                      ": level=" + loggingMXBean.getLoggerLevel(logger) +
                      ", parent=" + loggingMXBean.getParentLoggerName(logger) + CRLF);
        }
        sb.append(CRLF);

        return sb.toString();
    }
}
