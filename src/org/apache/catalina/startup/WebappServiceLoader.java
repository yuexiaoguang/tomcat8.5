package org.apache.catalina.startup;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.regex.Pattern;

import javax.servlet.ServletContext;

import org.apache.catalina.Context;
import org.apache.tomcat.util.scan.JarFactory;

/**
 * 遵守web应用排除规则的Java的 JAR ServiceLoader的一个变量.
 * <p>
 * 主要用于像Servlet 8.2.4中定义的那样加载 ServletContainerInitializer. 此实现不会尝试懒加载，因为容器需要内省发现所有实现.
 * <p>
 * 如果 ServletContext 定义了ORDERED_LIBS, 只会搜索配置文件中定义的WEB-INF/lib下的 JAR;
 * 如果未定义ORDERED_LIBS, 将会搜索所有的 JAR. 总是返回父ClassLoader中资源定义的Provider.
 * <p>
 * 将使用上下文的ClassLoader加载Provider类.
 *
 * @param <T> 要加载的服务的类型
 */
public class WebappServiceLoader<T> {
    private static final String LIB = "/WEB-INF/lib/";
    private static final String SERVICES = "META-INF/services/";

    private final Context context;
    private final ServletContext servletContext;
    private final Pattern containerSciFilterPattern;

    /**
     * @param context 使用的上下文
     */
    public WebappServiceLoader(Context context) {
        this.context = context;
        this.servletContext = context.getServletContext();
        String containerSciFilter = context.getContainerSciFilter();
        if (containerSciFilter != null && containerSciFilter.length() > 0) {
            containerSciFilterPattern = Pattern.compile(containerSciFilter);
        } else {
            containerSciFilterPattern = null;
        }
    }

    /**
     * 加载服务类型的provider.
     *
     * @param serviceType 要加载的服务的类型
     * @return 不可更改的服务提供者集合
     * @throws IOException 加载服务存在问题
     */
    public List<T> load(Class<T> serviceType) throws IOException {
        String configFile = SERVICES + serviceType.getName();

        LinkedHashSet<String> applicationServicesFound = new LinkedHashSet<>();
        LinkedHashSet<String> containerServicesFound = new LinkedHashSet<>();

        ClassLoader loader = servletContext.getClassLoader();

        // 如果ServletContext 有 ORDERED_LIBS, 那么使用它指定用于加载服务的WEB-INF/lib中的JAR的集合
        @SuppressWarnings("unchecked")
        List<String> orderedLibs =
                (List<String>) servletContext.getAttribute(ServletContext.ORDERED_LIBS);
        if (orderedLibs != null) {
            // handle ordered libs directly, ...
            for (String lib : orderedLibs) {
                URL jarUrl = servletContext.getResource(LIB + lib);
                if (jarUrl == null) {
                    // should not happen, just ignore
                    continue;
                }

                String base = jarUrl.toExternalForm();
                URL url;
                if (base.endsWith("/")) {
                    url = new URL(base + configFile);
                } else {
                    url = JarFactory.getJarEntryURL(jarUrl, configFile);
                }
                try {
                    parseConfigFile(applicationServicesFound, url);
                } catch (FileNotFoundException e) {
                    // no provider file found, this is OK
                }
            }

            // and the parent ClassLoader for all others
            loader = context.getParentClassLoader();
        }

        Enumeration<URL> resources;
        if (loader == null) {
            resources = ClassLoader.getSystemResources(configFile);
        } else {
            resources = loader.getResources(configFile);
        }
        while (resources.hasMoreElements()) {
            parseConfigFile(containerServicesFound, resources.nextElement());
        }

        // Filter the discovered container SCIs if required
        if (containerSciFilterPattern != null) {
            Iterator<String> iter = containerServicesFound.iterator();
            while (iter.hasNext()) {
                if (containerSciFilterPattern.matcher(iter.next()).find()) {
                    iter.remove();
                }
            }
        }

        // 在容器服务之后添加应用服务，确保首先加载容器服务
        containerServicesFound.addAll(applicationServicesFound);

        // load the discovered services
        if (containerServicesFound.isEmpty()) {
            return Collections.emptyList();
        }
        return loadServices(serviceType, containerServicesFound);
    }

    void parseConfigFile(LinkedHashSet<String> servicesFound, URL url)
            throws IOException {
        try (InputStream is = url.openStream();
            InputStreamReader in = new InputStreamReader(is, StandardCharsets.UTF_8);
            BufferedReader reader = new BufferedReader(in);) {
            String line;
            while ((line = reader.readLine()) != null) {
                int i = line.indexOf('#');
                if (i >= 0) {
                    line = line.substring(0, i);
                }
                line = line.trim();
                if (line.length() == 0) {
                    continue;
                }
                servicesFound.add(line);
            }
        }
    }

    List<T> loadServices(Class<T> serviceType, LinkedHashSet<String> servicesFound)
            throws IOException {
        ClassLoader loader = servletContext.getClassLoader();
        List<T> services = new ArrayList<>(servicesFound.size());
        for (String serviceClass : servicesFound) {
            try {
                Class<?> clazz = Class.forName(serviceClass, true, loader);
                services.add(serviceType.cast(clazz.getConstructor().newInstance()));
            } catch (ReflectiveOperationException | ClassCastException e) {
                throw new IOException(e);
            }
        }
        return Collections.unmodifiableList(services);
    }
}
