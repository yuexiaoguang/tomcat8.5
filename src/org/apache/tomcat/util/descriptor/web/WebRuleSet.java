package org.apache.tomcat.util.descriptor.web;

import java.lang.reflect.Method;
import java.util.ArrayList;

import org.apache.tomcat.util.IntrospectionUtils;
import org.apache.tomcat.util.digester.CallMethodRule;
import org.apache.tomcat.util.digester.CallParamRule;
import org.apache.tomcat.util.digester.Digester;
import org.apache.tomcat.util.digester.Rule;
import org.apache.tomcat.util.digester.RuleSetBase;
import org.apache.tomcat.util.digester.SetNextRule;
import org.apache.tomcat.util.res.StringManager;
import org.xml.sax.Attributes;


/**
 * <p>用于处理Web应用程序部署描述符(<code>/WEB-INF/web.xml</code>)资源的内容.</p>
 */
@SuppressWarnings("deprecation")
public class WebRuleSet extends RuleSetBase {

    protected static final StringManager sm =
        StringManager.getManager(Constants.PACKAGE_NAME);

    // ----------------------------------------------------- Instance Variables


    /**
     * 用于识别元素的匹配模式前缀.
     */
    protected final String prefix;

    /**
     * 完整模式匹配前缀, 包括webapp或web-fragment组件, 用于匹配元素
     */
    protected final String fullPrefix;

    /**
     * 此规则集是否用于web-fragment.xml文件或web.xml文件.
     */
    protected final boolean fragment;

    /**
     * 用于解析web.xml的<code>SetSessionConfig</code>规则
     */
    protected final SetSessionConfig sessionConfig = new SetSessionConfig();


    /**
     * 用于解析web.xml的<code>SetLoginConfig</code>规则
     */
    protected final SetLoginConfig loginConfig = new SetLoginConfig();


    /**
     * 用于解析web.xml的<code>SetJspConfig</code>规则
     */
    protected final SetJspConfig jspConfig = new SetJspConfig();


    /**
     * 用于解析web.xml的<code>NameRule</code>规则
     */
    protected final NameRule name = new NameRule();


    /**
     * 用于解析web.xml的<code>AbsoluteOrderingRule</code>规则
     */
    protected final AbsoluteOrderingRule absoluteOrdering;


    /**
     * 用于解析web.xml的<code>RelativeOrderingRule</code>规则
     */
    protected final RelativeOrderingRule relativeOrdering;



    // ------------------------------------------------------------ Constructor


    public WebRuleSet() {
        this("", false);
    }


    /**
     * @param fragment <code>true</code>如果这是一个Web片段
     */
    public WebRuleSet(boolean fragment) {
        this("", fragment);
    }


    /**
     * @param prefix 匹配模式规则的前缀 (包括尾部斜杠字符)
     * @param fragment <code>true</code>如果这是一个Web片段
     */
    public WebRuleSet(String prefix, boolean fragment) {
        super();
        this.prefix = prefix;
        this.fragment = fragment;

        if(fragment) {
            fullPrefix = prefix + "web-fragment";
        } else {
            fullPrefix = prefix + "web-app";
        }

        absoluteOrdering = new AbsoluteOrderingRule(fragment);
        relativeOrdering = new RelativeOrderingRule(fragment);
    }


    // --------------------------------------------------------- Public Methods

    /**
     * <p>将此RuleSet中定义的Rule实例集添加到指定的<code>Digester</code>实例, 并将它们与命名空间URI相关联.
     * 此方法只能由Digester实例调用.</p>
     *
     * @param digester 应添加Rule实例的Digester实例.
     */
    @Override
    public void addRuleInstances(Digester digester) {
        digester.addRule(fullPrefix,
                         new SetPublicIdRule("setPublicId"));
        digester.addRule(fullPrefix,
                         new IgnoreAnnotationsRule());
        digester.addRule(fullPrefix,
                new VersionRule());

        // 片段和非片段都需要
        digester.addRule(fullPrefix + "/absolute-ordering", absoluteOrdering);
        digester.addRule(fullPrefix + "/ordering", relativeOrdering);

        if (fragment) {
            // web-fragment.xml
            digester.addRule(fullPrefix + "/name", name);
            digester.addCallMethod(fullPrefix + "/ordering/after/name",
                                   "addAfterOrdering", 0);
            digester.addCallMethod(fullPrefix + "/ordering/after/others",
                                   "addAfterOrderingOthers");
            digester.addCallMethod(fullPrefix + "/ordering/before/name",
                                   "addBeforeOrdering", 0);
            digester.addCallMethod(fullPrefix + "/ordering/before/others",
                                   "addBeforeOrderingOthers");
        } else {
            // web.xml
            digester.addCallMethod(fullPrefix + "/absolute-ordering/name",
                                   "addAbsoluteOrdering", 0);
            digester.addCallMethod(fullPrefix + "/absolute-ordering/others",
                                   "addAbsoluteOrderingOthers");
            digester.addRule(fullPrefix + "/deny-uncovered-http-methods",
                    new SetDenyUncoveredHttpMethodsRule());
        }

        digester.addCallMethod(fullPrefix + "/context-param",
                               "addContextParam", 2);
        digester.addCallParam(fullPrefix + "/context-param/param-name", 0);
        digester.addCallParam(fullPrefix + "/context-param/param-value", 1);

        digester.addCallMethod(fullPrefix + "/display-name",
                               "setDisplayName", 0);

        digester.addRule(fullPrefix + "/distributable",
                         new SetDistributableRule());

        configureNamingRules(digester);

        digester.addObjectCreate(fullPrefix + "/error-page",
                                 "org.apache.tomcat.util.descriptor.web.ErrorPage");
        digester.addSetNext(fullPrefix + "/error-page",
                            "addErrorPage",
                            "org.apache.tomcat.util.descriptor.web.ErrorPage");

        digester.addCallMethod(fullPrefix + "/error-page/error-code",
                               "setErrorCode", 0);
        digester.addCallMethod(fullPrefix + "/error-page/exception-type",
                               "setExceptionType", 0);
        digester.addCallMethod(fullPrefix + "/error-page/location",
                               "setLocation", 0);

        digester.addObjectCreate(fullPrefix + "/filter",
                                 "org.apache.tomcat.util.descriptor.web.FilterDef");
        digester.addSetNext(fullPrefix + "/filter",
                            "addFilter",
                            "org.apache.tomcat.util.descriptor.web.FilterDef");

        digester.addCallMethod(fullPrefix + "/filter/description",
                               "setDescription", 0);
        digester.addCallMethod(fullPrefix + "/filter/display-name",
                               "setDisplayName", 0);
        digester.addCallMethod(fullPrefix + "/filter/filter-class",
                               "setFilterClass", 0);
        digester.addCallMethod(fullPrefix + "/filter/filter-name",
                               "setFilterName", 0);
        digester.addCallMethod(fullPrefix + "/filter/icon/large-icon",
                               "setLargeIcon", 0);
        digester.addCallMethod(fullPrefix + "/filter/icon/small-icon",
                               "setSmallIcon", 0);
        digester.addCallMethod(fullPrefix + "/filter/async-supported",
                "setAsyncSupported", 0);

        digester.addCallMethod(fullPrefix + "/filter/init-param",
                               "addInitParameter", 2);
        digester.addCallParam(fullPrefix + "/filter/init-param/param-name",
                              0);
        digester.addCallParam(fullPrefix + "/filter/init-param/param-value",
                              1);

        digester.addObjectCreate(fullPrefix + "/filter-mapping",
                                 "org.apache.tomcat.util.descriptor.web.FilterMap");
        digester.addSetNext(fullPrefix + "/filter-mapping",
                                 "addFilterMapping",
                                 "org.apache.tomcat.util.descriptor.web.FilterMap");

        digester.addCallMethod(fullPrefix + "/filter-mapping/filter-name",
                               "setFilterName", 0);
        digester.addCallMethod(fullPrefix + "/filter-mapping/servlet-name",
                               "addServletName", 0);
        digester.addCallMethod(fullPrefix + "/filter-mapping/url-pattern",
                               "addURLPattern", 0);

        digester.addCallMethod(fullPrefix + "/filter-mapping/dispatcher",
                               "setDispatcher", 0);

         digester.addCallMethod(fullPrefix + "/listener/listener-class",
                                "addListener", 0);

        digester.addRule(fullPrefix + "/jsp-config",
                         jspConfig);

        digester.addObjectCreate(fullPrefix + "/jsp-config/jsp-property-group",
                                 "org.apache.tomcat.util.descriptor.web.JspPropertyGroup");
        digester.addSetNext(fullPrefix + "/jsp-config/jsp-property-group",
                            "addJspPropertyGroup",
                            "org.apache.tomcat.util.descriptor.web.JspPropertyGroup");
        digester.addCallMethod(fullPrefix + "/jsp-config/jsp-property-group/deferred-syntax-allowed-as-literal",
                               "setDeferredSyntax", 0);
        digester.addCallMethod(fullPrefix + "/jsp-config/jsp-property-group/el-ignored",
                               "setElIgnored", 0);
        digester.addCallMethod(fullPrefix + "/jsp-config/jsp-property-group/include-coda",
                               "addIncludeCoda", 0);
        digester.addCallMethod(fullPrefix + "/jsp-config/jsp-property-group/include-prelude",
                               "addIncludePrelude", 0);
        digester.addCallMethod(fullPrefix + "/jsp-config/jsp-property-group/is-xml",
                               "setIsXml", 0);
        digester.addCallMethod(fullPrefix + "/jsp-config/jsp-property-group/page-encoding",
                               "setPageEncoding", 0);
        digester.addCallMethod(fullPrefix + "/jsp-config/jsp-property-group/scripting-invalid",
                               "setScriptingInvalid", 0);
        digester.addCallMethod(fullPrefix + "/jsp-config/jsp-property-group/trim-directive-whitespaces",
                               "setTrimWhitespace", 0);
        digester.addCallMethod(fullPrefix + "/jsp-config/jsp-property-group/url-pattern",
                               "addUrlPattern", 0);
        digester.addCallMethod(fullPrefix + "/jsp-config/jsp-property-group/default-content-type",
                               "setDefaultContentType", 0);
        digester.addCallMethod(fullPrefix + "/jsp-config/jsp-property-group/buffer",
                               "setBuffer", 0);
        digester.addCallMethod(fullPrefix + "/jsp-config/jsp-property-group/error-on-undeclared-namespace",
                               "setErrorOnUndeclaredNamespace", 0);

        digester.addRule(fullPrefix + "/login-config",
                         loginConfig);

        digester.addObjectCreate(fullPrefix + "/login-config",
                                 "org.apache.tomcat.util.descriptor.web.LoginConfig");
        digester.addSetNext(fullPrefix + "/login-config",
                            "setLoginConfig",
                            "org.apache.tomcat.util.descriptor.web.LoginConfig");

        digester.addCallMethod(fullPrefix + "/login-config/auth-method",
                               "setAuthMethod", 0);
        digester.addCallMethod(fullPrefix + "/login-config/realm-name",
                               "setRealmName", 0);
        digester.addCallMethod(fullPrefix + "/login-config/form-login-config/form-error-page",
                               "setErrorPage", 0);
        digester.addCallMethod(fullPrefix + "/login-config/form-login-config/form-login-page",
                               "setLoginPage", 0);

        digester.addCallMethod(fullPrefix + "/mime-mapping",
                               "addMimeMapping", 2);
        digester.addCallParam(fullPrefix + "/mime-mapping/extension", 0);
        digester.addCallParam(fullPrefix + "/mime-mapping/mime-type", 1);


        digester.addObjectCreate(fullPrefix + "/security-constraint",
                                 "org.apache.tomcat.util.descriptor.web.SecurityConstraint");
        digester.addSetNext(fullPrefix + "/security-constraint",
                            "addSecurityConstraint",
                            "org.apache.tomcat.util.descriptor.web.SecurityConstraint");

        digester.addRule(fullPrefix + "/security-constraint/auth-constraint",
                         new SetAuthConstraintRule());
        digester.addCallMethod(fullPrefix + "/security-constraint/auth-constraint/role-name",
                               "addAuthRole", 0);
        digester.addCallMethod(fullPrefix + "/security-constraint/display-name",
                               "setDisplayName", 0);
        digester.addCallMethod(fullPrefix + "/security-constraint/user-data-constraint/transport-guarantee",
                               "setUserConstraint", 0);

        digester.addObjectCreate(fullPrefix + "/security-constraint/web-resource-collection",
                                 "org.apache.tomcat.util.descriptor.web.SecurityCollection");
        digester.addSetNext(fullPrefix + "/security-constraint/web-resource-collection",
                            "addCollection",
                            "org.apache.tomcat.util.descriptor.web.SecurityCollection");
        digester.addCallMethod(fullPrefix + "/security-constraint/web-resource-collection/http-method",
                               "addMethod", 0);
        digester.addCallMethod(fullPrefix + "/security-constraint/web-resource-collection/http-method-omission",
                               "addOmittedMethod", 0);
        digester.addCallMethod(fullPrefix + "/security-constraint/web-resource-collection/url-pattern",
                               "addPattern", 0);
        digester.addCallMethod(fullPrefix + "/security-constraint/web-resource-collection/web-resource-name",
                               "setName", 0);

        digester.addCallMethod(fullPrefix + "/security-role/role-name",
                               "addSecurityRole", 0);

        digester.addRule(fullPrefix + "/servlet",
                         new ServletDefCreateRule());
        digester.addSetNext(fullPrefix + "/servlet",
                            "addServlet",
                            "org.apache.tomcat.util.descriptor.web.ServletDef");

        digester.addCallMethod(fullPrefix + "/servlet/init-param",
                               "addInitParameter", 2);
        digester.addCallParam(fullPrefix + "/servlet/init-param/param-name",
                              0);
        digester.addCallParam(fullPrefix + "/servlet/init-param/param-value",
                              1);

        digester.addCallMethod(fullPrefix + "/servlet/jsp-file",
                               "setJspFile", 0);
        digester.addCallMethod(fullPrefix + "/servlet/load-on-startup",
                               "setLoadOnStartup", 0);
        digester.addCallMethod(fullPrefix + "/servlet/run-as/role-name",
                               "setRunAs", 0);

        digester.addObjectCreate(fullPrefix + "/servlet/security-role-ref",
                                 "org.apache.tomcat.util.descriptor.web.SecurityRoleRef");
        digester.addSetNext(fullPrefix + "/servlet/security-role-ref",
                            "addSecurityRoleRef",
                            "org.apache.tomcat.util.descriptor.web.SecurityRoleRef");
        digester.addCallMethod(fullPrefix + "/servlet/security-role-ref/role-link",
                               "setLink", 0);
        digester.addCallMethod(fullPrefix + "/servlet/security-role-ref/role-name",
                               "setName", 0);

        digester.addCallMethod(fullPrefix + "/servlet/servlet-class",
                              "setServletClass", 0);
        digester.addCallMethod(fullPrefix + "/servlet/servlet-name",
                              "setServletName", 0);

        digester.addObjectCreate(fullPrefix + "/servlet/multipart-config",
                                 "org.apache.tomcat.util.descriptor.web.MultipartDef");
        digester.addSetNext(fullPrefix + "/servlet/multipart-config",
                            "setMultipartDef",
                            "org.apache.tomcat.util.descriptor.web.MultipartDef");
        digester.addCallMethod(fullPrefix + "/servlet/multipart-config/location",
                               "setLocation", 0);
        digester.addCallMethod(fullPrefix + "/servlet/multipart-config/max-file-size",
                               "setMaxFileSize", 0);
        digester.addCallMethod(fullPrefix + "/servlet/multipart-config/max-request-size",
                               "setMaxRequestSize", 0);
        digester.addCallMethod(fullPrefix + "/servlet/multipart-config/file-size-threshold",
                               "setFileSizeThreshold", 0);

        digester.addCallMethod(fullPrefix + "/servlet/async-supported",
                               "setAsyncSupported", 0);
        digester.addCallMethod(fullPrefix + "/servlet/enabled",
                               "setEnabled", 0);


        digester.addRule(fullPrefix + "/servlet-mapping",
                               new CallMethodMultiRule("addServletMapping", 2, 0));
        digester.addCallParam(fullPrefix + "/servlet-mapping/servlet-name", 1);
        digester.addRule(fullPrefix + "/servlet-mapping/url-pattern", new CallParamMultiRule(0));

        digester.addRule(fullPrefix + "/session-config", sessionConfig);
        digester.addObjectCreate(fullPrefix + "/session-config",
                                 "org.apache.tomcat.util.descriptor.web.SessionConfig");
        digester.addSetNext(fullPrefix + "/session-config", "setSessionConfig",
                            "org.apache.tomcat.util.descriptor.web.SessionConfig");
        digester.addCallMethod(fullPrefix + "/session-config/session-timeout",
                               "setSessionTimeout", 0);
        digester.addCallMethod(fullPrefix + "/session-config/cookie-config/name",
                               "setCookieName", 0);
        digester.addCallMethod(fullPrefix + "/session-config/cookie-config/domain",
                               "setCookieDomain", 0);
        digester.addCallMethod(fullPrefix + "/session-config/cookie-config/path",
                               "setCookiePath", 0);
        digester.addCallMethod(fullPrefix + "/session-config/cookie-config/comment",
                               "setCookieComment", 0);
        digester.addCallMethod(fullPrefix + "/session-config/cookie-config/http-only",
                               "setCookieHttpOnly", 0);
        digester.addCallMethod(fullPrefix + "/session-config/cookie-config/secure",
                               "setCookieSecure", 0);
        digester.addCallMethod(fullPrefix + "/session-config/cookie-config/max-age",
                               "setCookieMaxAge", 0);
        digester.addCallMethod(fullPrefix + "/session-config/tracking-mode",
                               "addSessionTrackingMode", 0);

        // Taglibs pre Servlet 2.4
        digester.addRule(fullPrefix + "/taglib", new TaglibLocationRule(false));
        digester.addCallMethod(fullPrefix + "/taglib",
                               "addTaglib", 2);
        digester.addCallParam(fullPrefix + "/taglib/taglib-location", 1);
        digester.addCallParam(fullPrefix + "/taglib/taglib-uri", 0);

        // Taglibs Servlet 2.4 onwards
        digester.addRule(fullPrefix + "/jsp-config/taglib", new TaglibLocationRule(true));
        digester.addCallMethod(fullPrefix + "/jsp-config/taglib",
                "addTaglib", 2);
        digester.addCallParam(fullPrefix + "/jsp-config/taglib/taglib-location", 1);
        digester.addCallParam(fullPrefix + "/jsp-config/taglib/taglib-uri", 0);

        digester.addCallMethod(fullPrefix + "/welcome-file-list/welcome-file",
                               "addWelcomeFile", 0);

        digester.addCallMethod(fullPrefix + "/locale-encoding-mapping-list/locale-encoding-mapping",
                              "addLocaleEncodingMapping", 2);
        digester.addCallParam(fullPrefix + "/locale-encoding-mapping-list/locale-encoding-mapping/locale", 0);
        digester.addCallParam(fullPrefix + "/locale-encoding-mapping-list/locale-encoding-mapping/encoding", 1);

        digester.addRule(fullPrefix + "/post-construct",
                new LifecycleCallbackRule("addPostConstructMethods", 2, true));
        digester.addCallParam(fullPrefix + "/post-construct/lifecycle-callback-class", 0);
        digester.addCallParam(fullPrefix + "/post-construct/lifecycle-callback-method", 1);

        digester.addRule(fullPrefix + "/pre-destroy",
                new LifecycleCallbackRule("addPreDestroyMethods", 2, false));
        digester.addCallParam(fullPrefix + "/pre-destroy/lifecycle-callback-class", 0);
        digester.addCallParam(fullPrefix + "/pre-destroy/lifecycle-callback-method", 1);
    }

    protected void configureNamingRules(Digester digester) {
        //ejb-local-ref
        digester.addObjectCreate(fullPrefix + "/ejb-local-ref",
                                 "org.apache.tomcat.util.descriptor.web.ContextLocalEjb");
        digester.addSetNext(fullPrefix + "/ejb-local-ref",
                            "addEjbLocalRef",
                            "org.apache.tomcat.util.descriptor.web.ContextLocalEjb");
        digester.addCallMethod(fullPrefix + "/ejb-local-ref/description",
                               "setDescription", 0);
        digester.addCallMethod(fullPrefix + "/ejb-local-ref/ejb-link",
                               "setLink", 0);
        digester.addCallMethod(fullPrefix + "/ejb-local-ref/ejb-ref-name",
                               "setName", 0);
        digester.addCallMethod(fullPrefix + "/ejb-local-ref/ejb-ref-type",
                               "setType", 0);
        digester.addCallMethod(fullPrefix + "/ejb-local-ref/local",
                               "setLocal", 0);
        digester.addCallMethod(fullPrefix + "/ejb-local-ref/local-home",
                               "setHome", 0);
        digester.addRule(fullPrefix + "/ejb-local-ref/mapped-name",
                         new MappedNameRule());
        configureInjectionRules(digester, "web-app/ejb-local-ref/");

        //ejb-ref
        digester.addObjectCreate(fullPrefix + "/ejb-ref",
                                 "org.apache.tomcat.util.descriptor.web.ContextEjb");
        digester.addSetNext(fullPrefix + "/ejb-ref",
                            "addEjbRef",
                            "org.apache.tomcat.util.descriptor.web.ContextEjb");
        digester.addCallMethod(fullPrefix + "/ejb-ref/description",
                               "setDescription", 0);
        digester.addCallMethod(fullPrefix + "/ejb-ref/ejb-link",
                               "setLink", 0);
        digester.addCallMethod(fullPrefix + "/ejb-ref/ejb-ref-name",
                               "setName", 0);
        digester.addCallMethod(fullPrefix + "/ejb-ref/ejb-ref-type",
                               "setType", 0);
        digester.addCallMethod(fullPrefix + "/ejb-ref/home",
                               "setHome", 0);
        digester.addCallMethod(fullPrefix + "/ejb-ref/remote",
                               "setRemote", 0);
        digester.addRule(fullPrefix + "/ejb-ref/mapped-name",
                         new MappedNameRule());
        configureInjectionRules(digester, "web-app/ejb-ref/");

        //env-entry
        digester.addObjectCreate(fullPrefix + "/env-entry",
                                 "org.apache.tomcat.util.descriptor.web.ContextEnvironment");
        digester.addSetNext(fullPrefix + "/env-entry",
                            "addEnvEntry",
                            "org.apache.tomcat.util.descriptor.web.ContextEnvironment");
        digester.addRule(fullPrefix + "/env-entry", new SetOverrideRule());
        digester.addCallMethod(fullPrefix + "/env-entry/description",
                               "setDescription", 0);
        digester.addCallMethod(fullPrefix + "/env-entry/env-entry-name",
                               "setName", 0);
        digester.addCallMethod(fullPrefix + "/env-entry/env-entry-type",
                               "setType", 0);
        digester.addCallMethod(fullPrefix + "/env-entry/env-entry-value",
                               "setValue", 0);
        digester.addRule(fullPrefix + "/env-entry/mapped-name",
                         new MappedNameRule());
        configureInjectionRules(digester, "web-app/env-entry/");

        //resource-env-ref
        digester.addObjectCreate(fullPrefix + "/resource-env-ref",
            "org.apache.tomcat.util.descriptor.web.ContextResourceEnvRef");
        digester.addSetNext(fullPrefix + "/resource-env-ref",
                            "addResourceEnvRef",
                            "org.apache.tomcat.util.descriptor.web.ContextResourceEnvRef");
        digester.addCallMethod(fullPrefix + "/resource-env-ref/resource-env-ref-name",
                "setName", 0);
        digester.addCallMethod(fullPrefix + "/resource-env-ref/resource-env-ref-type",
                "setType", 0);
        digester.addRule(fullPrefix + "/resource-env-ref/mapped-name",
                         new MappedNameRule());
        configureInjectionRules(digester, "web-app/resource-env-ref/");

        //message-destination
        digester.addObjectCreate(fullPrefix + "/message-destination",
                                 "org.apache.tomcat.util.descriptor.web.MessageDestination");
        digester.addSetNext(fullPrefix + "/message-destination",
                            "addMessageDestination",
                            "org.apache.tomcat.util.descriptor.web.MessageDestination");
        digester.addCallMethod(fullPrefix + "/message-destination/description",
                               "setDescription", 0);
        digester.addCallMethod(fullPrefix + "/message-destination/display-name",
                               "setDisplayName", 0);
        digester.addCallMethod(fullPrefix + "/message-destination/icon/large-icon",
                               "setLargeIcon", 0);
        digester.addCallMethod(fullPrefix + "/message-destination/icon/small-icon",
                               "setSmallIcon", 0);
        digester.addCallMethod(fullPrefix + "/message-destination/message-destination-name",
                               "setName", 0);
        digester.addRule(fullPrefix + "/message-destination/mapped-name",
                         new MappedNameRule());

        //message-destination-ref
        digester.addObjectCreate(fullPrefix + "/message-destination-ref",
                                 "org.apache.tomcat.util.descriptor.web.MessageDestinationRef");
        digester.addSetNext(fullPrefix + "/message-destination-ref",
                            "addMessageDestinationRef",
                            "org.apache.tomcat.util.descriptor.web.MessageDestinationRef");
        digester.addCallMethod(fullPrefix + "/message-destination-ref/description",
                               "setDescription", 0);
        digester.addCallMethod(fullPrefix + "/message-destination-ref/message-destination-link",
                               "setLink", 0);
        digester.addCallMethod(fullPrefix + "/message-destination-ref/message-destination-ref-name",
                               "setName", 0);
        digester.addCallMethod(fullPrefix + "/message-destination-ref/message-destination-type",
                               "setType", 0);
        digester.addCallMethod(fullPrefix + "/message-destination-ref/message-destination-usage",
                               "setUsage", 0);
        digester.addRule(fullPrefix + "/message-destination-ref/mapped-name",
                         new MappedNameRule());
        configureInjectionRules(digester, "web-app/message-destination-ref/");

        //resource-ref
        digester.addObjectCreate(fullPrefix + "/resource-ref",
                                 "org.apache.tomcat.util.descriptor.web.ContextResource");
        digester.addSetNext(fullPrefix + "/resource-ref",
                            "addResourceRef",
                            "org.apache.tomcat.util.descriptor.web.ContextResource");
        digester.addCallMethod(fullPrefix + "/resource-ref/description",
                               "setDescription", 0);
        digester.addCallMethod(fullPrefix + "/resource-ref/res-auth",
                               "setAuth", 0);
        digester.addCallMethod(fullPrefix + "/resource-ref/res-ref-name",
                               "setName", 0);
        digester.addCallMethod(fullPrefix + "/resource-ref/res-sharing-scope",
                               "setScope", 0);
        digester.addCallMethod(fullPrefix + "/resource-ref/res-type",
                               "setType", 0);
        digester.addRule(fullPrefix + "/resource-ref/mapped-name",
                         new MappedNameRule());
        configureInjectionRules(digester, "web-app/resource-ref/");

        //service-ref
        digester.addObjectCreate(fullPrefix + "/service-ref",
                                 "org.apache.tomcat.util.descriptor.web.ContextService");
        digester.addSetNext(fullPrefix + "/service-ref",
                            "addServiceRef",
                            "org.apache.tomcat.util.descriptor.web.ContextService");
        digester.addCallMethod(fullPrefix + "/service-ref/description",
                               "setDescription", 0);
        digester.addCallMethod(fullPrefix + "/service-ref/display-name",
                               "setDisplayname", 0);
        digester.addCallMethod(fullPrefix + "/service-ref/icon/large-icon",
                               "setLargeIcon", 0);
        digester.addCallMethod(fullPrefix + "/service-ref/icon/small-icon",
                               "setSmallIcon", 0);
        digester.addCallMethod(fullPrefix + "/service-ref/service-ref-name",
                               "setName", 0);
        digester.addCallMethod(fullPrefix + "/service-ref/service-interface",
                               "setInterface", 0);
        digester.addCallMethod(fullPrefix + "/service-ref/service-ref-type",
                               "setType", 0);
        digester.addCallMethod(fullPrefix + "/service-ref/wsdl-file",
                               "setWsdlfile", 0);
        digester.addCallMethod(fullPrefix + "/service-ref/jaxrpc-mapping-file",
                               "setJaxrpcmappingfile", 0);
        digester.addRule(fullPrefix + "/service-ref/service-qname", new ServiceQnameRule());

        digester.addRule(fullPrefix + "/service-ref/port-component-ref",
                               new CallMethodMultiRule("addPortcomponent", 2, 1));
        digester.addCallParam(fullPrefix + "/service-ref/port-component-ref/service-endpoint-interface", 0);
        digester.addRule(fullPrefix + "/service-ref/port-component-ref/port-component-link", new CallParamMultiRule(1));

        digester.addObjectCreate(fullPrefix + "/service-ref/handler",
                                 "org.apache.tomcat.util.descriptor.web.ContextHandler");
        digester.addRule(fullPrefix + "/service-ref/handler",
                         new SetNextRule("addHandler",
                         "org.apache.tomcat.util.descriptor.web.ContextHandler"));

        digester.addCallMethod(fullPrefix + "/service-ref/handler/handler-name",
                               "setName", 0);
        digester.addCallMethod(fullPrefix + "/service-ref/handler/handler-class",
                               "setHandlerclass", 0);

        digester.addCallMethod(fullPrefix + "/service-ref/handler/init-param",
                               "setProperty", 2);
        digester.addCallParam(fullPrefix + "/service-ref/handler/init-param/param-name",
                              0);
        digester.addCallParam(fullPrefix + "/service-ref/handler/init-param/param-value",
                              1);

        digester.addRule(fullPrefix + "/service-ref/handler/soap-header", new SoapHeaderRule());

        digester.addCallMethod(fullPrefix + "/service-ref/handler/soap-role",
                               "addSoapRole", 0);
        digester.addCallMethod(fullPrefix + "/service-ref/handler/port-name",
                               "addPortName", 0);
        digester.addRule(fullPrefix + "/service-ref/mapped-name",
                         new MappedNameRule());
        configureInjectionRules(digester, "web-app/service-ref/");
    }

    protected void configureInjectionRules(Digester digester, String base) {

        digester.addCallMethod(prefix + base + "injection-target", "addInjectionTarget", 2);
        digester.addCallParam(prefix + base + "injection-target/injection-target-class", 0);
        digester.addCallParam(prefix + base + "injection-target/injection-target-name", 1);

    }


    /**
     * 重置用于验证web.xml文件的计数器.
     */
    public void recycle(){
        jspConfig.isJspConfigSet = false;
        sessionConfig.isSessionConfigSet = false;
        loginConfig.isLoginConfigSet = false;
        name.isNameSet = false;
        absoluteOrdering.isAbsoluteOrderingSet = false;
        relativeOrdering.isRelativeOrderingSet = false;
    }
}


// ----------------------------------------------------------- Private Classes


/**
 * 检查<code>login-config</code>在web.xml中只出现一次的规则
 */
final class SetLoginConfig extends Rule {
    boolean isLoginConfigSet = false;
    public SetLoginConfig() {
        // NO-OP
    }

    @Override
    public void begin(String namespace, String name, Attributes attributes)
        throws Exception {
        if (isLoginConfigSet){
            throw new IllegalArgumentException(
            "<login-config> element is limited to 1 occurrence");
        }
        isLoginConfigSet = true;
    }

}


/**
 * 检查<code>jsp-config</code>在web.xml中只出现一次的规则
 */
final class SetJspConfig extends Rule {
    boolean isJspConfigSet = false;
    public SetJspConfig() {
        // NO-OP
    }

    @Override
    public void begin(String namespace, String name, Attributes attributes)
        throws Exception {
        if (isJspConfigSet){
            throw new IllegalArgumentException(
            "<jsp-config> element is limited to 1 occurrence");
        }
        isJspConfigSet = true;
    }

}


/**
 * 检查<code>session-config</code>在web.xml中只出现一次的规则
 */
final class SetSessionConfig extends Rule {
    boolean isSessionConfigSet = false;
    public SetSessionConfig() {
        // NO-OP
    }

    @Override
    public void begin(String namespace, String name, Attributes attributes)
        throws Exception {
        if (isSessionConfigSet){
            throw new IllegalArgumentException(
            "<session-config> element is limited to 1 occurrence");
        }
        isSessionConfigSet = true;
    }

}

/**
 * 调用堆栈顶部项的<code>setAuthConstraint(true)</code>方法的规则, 
 * 该方法的类型必须为<code>org.apache.tomcat.util.descriptor.web.SecurityConstraint</code>.
 */
final class SetAuthConstraintRule extends Rule {

    public SetAuthConstraintRule() {
        // NO-OP
    }

    @Override
    public void begin(String namespace, String name, Attributes attributes)
        throws Exception {
        SecurityConstraint securityConstraint =
            (SecurityConstraint) digester.peek();
        securityConstraint.setAuthConstraint(true);
        if (digester.getLogger().isDebugEnabled()) {
            digester.getLogger()
               .debug("Calling SecurityConstraint.setAuthConstraint(true)");
        }
    }

}


/**
 * 为堆栈顶部对象调用<code>setDistributable(true)</code>的类, 必须是一个 {@link WebXml} 实例.
 */
final class SetDistributableRule extends Rule {

    public SetDistributableRule() {
        // NO-OP
    }

    @Override
    public void begin(String namespace, String name, Attributes attributes)
        throws Exception {
        WebXml webXml = (WebXml) digester.peek();
        webXml.setDistributable(true);
        if (digester.getLogger().isDebugEnabled()) {
            digester.getLogger().debug
               (webXml.getClass().getName() + ".setDistributable(true)");
        }
    }
}


/**
 * 为堆栈顶部对象调用<code>setDenyUncoveredHttpMethods(true)</code>的类, 必须是一个 {@link WebXml} 实例.
 */
final class SetDenyUncoveredHttpMethodsRule extends Rule {

    public SetDenyUncoveredHttpMethodsRule() {
        // NO-OP
    }

    @Override
    public void begin(String namespace, String name, Attributes attributes)
        throws Exception {
        WebXml webXml = (WebXml) digester.peek();
        webXml.setDenyUncoveredHttpMethods(true);
        if (digester.getLogger().isDebugEnabled()) {
            digester.getLogger().debug(webXml.getClass().getName() +
                    ".setDenyUncoveredHttpMethods(true)");
        }
    }
}


/**
 * 调用堆栈顶部对象的属性Setter的类, 传递当前正在处理的实体的公共ID.
 */
final class SetPublicIdRule extends Rule {

    public SetPublicIdRule(String method) {
        this.method = method;
    }

    private String method = null;

    @Override
    public void begin(String namespace, String name, Attributes attributes)
        throws Exception {

        Object top = digester.peek();
        Class<?> paramClasses[] = new Class[1];
        paramClasses[0] = "String".getClass();
        String paramValues[] = new String[1];
        paramValues[0] = digester.getPublicId();

        Method m = null;
        try {
            m = top.getClass().getMethod(method, paramClasses);
        } catch (NoSuchMethodException e) {
            digester.getLogger().error("Can't find method " + method + " in "
                                       + top + " CLASS " + top.getClass());
            return;
        }

        m.invoke(top, (Object [])paramValues);
        if (digester.getLogger().isDebugEnabled())
            digester.getLogger().debug("" + top.getClass().getName() + "."
                                       + method + "(" + paramValues[0] + ")");

    }

}


/**
 * 调用指定Context上的factory方法来创建要添加到堆栈的对象的规则.
 */
final class ServletDefCreateRule extends Rule {

    public ServletDefCreateRule() {
        // NO-OP
    }

    @Override
    public void begin(String namespace, String name, Attributes attributes)
        throws Exception {
        ServletDef servletDef = new ServletDef();
        digester.push(servletDef);
        if (digester.getLogger().isDebugEnabled())
            digester.getLogger().debug("new " + servletDef.getClass().getName());
    }

    @Override
    public void end(String namespace, String name)
        throws Exception {
        ServletDef servletDef = (ServletDef) digester.pop();
        if (digester.getLogger().isDebugEnabled())
            digester.getLogger().debug("pop " + servletDef.getClass().getName());
    }

}


/**
 * 可用于根据需要多次调用方法的规则 (用于 addServletMapping).
 */
final class CallParamMultiRule extends CallParamRule {

    public CallParamMultiRule(int paramIndex) {
        super(paramIndex);
    }

    @Override
    public void end(String namespace, String name) {
        if (bodyTextStack != null && !bodyTextStack.empty()) {
            // 现在所做的是将一个参数推到顶部参数集上
            Object parameters[] = (Object[]) digester.peekParams();
            @SuppressWarnings("unchecked")
            ArrayList<String> params = (ArrayList<String>) parameters[paramIndex];
            if (params == null) {
                params = new ArrayList<>();
                parameters[paramIndex] = params;
            }
            params.add(bodyTextStack.pop());
        }
    }

}


/**
 * 可用于根据需要多次调用方法的规则 (用于 addServletMapping).
 */
final class CallMethodMultiRule extends CallMethodRule {

    final int multiParamIndex;

    public CallMethodMultiRule(String methodName, int paramCount, int multiParamIndex) {
        super(methodName, paramCount);
        this.multiParamIndex = multiParamIndex;
    }

    /**
     * 处理此元素的结尾.
     *
     * @param namespace 匹配元素的命名空间URI; 如果解析器不支持命名空间或元素没有命名空间, 则为空字符串
     * @param name 如果解析器是命名空间感知的本地名称; 或者只是元素名称
     */
    @Override
    public void end(String namespace, String name) throws Exception {

        // 检索或构造参数值数组
        Object parameters[] = null;
        if (paramCount > 0) {
            parameters = (Object[]) digester.popParams();
        } else {
            parameters = new Object[0];
            super.end(namespace, name);
        }

        ArrayList<?> multiParams = (ArrayList<?>) parameters[multiParamIndex];

        // 构造我们需要的参数值数组
        // 如果param值是String并且指定的paramType不是String, 只进行转换.
        Object paramValues[] = new Object[paramTypes.length];
        for (int i = 0; i < paramTypes.length; i++) {
            if (i != multiParamIndex) {
                // 转换空值并转换非stringy 参数类型的stringy参数
                if(parameters[i] == null || (parameters[i] instanceof String
                        && !String.class.isAssignableFrom(paramTypes[i]))) {
                    paramValues[i] =
                        IntrospectionUtils.convert((String) parameters[i], paramTypes[i]);
                } else {
                    paramValues[i] = parameters[i];
                }
            }
        }

        // 确定方法调用的目标对象
        Object target;
        if (targetOffset >= 0) {
            target = digester.peek(targetOffset);
        } else {
            target = digester.peek(digester.getCount() + targetOffset);
        }

        if (target == null) {
            StringBuilder sb = new StringBuilder();
            sb.append("[CallMethodRule]{");
            sb.append("");
            sb.append("} Call target is null (");
            sb.append("targetOffset=");
            sb.append(targetOffset);
            sb.append(",stackdepth=");
            sb.append(digester.getCount());
            sb.append(")");
            throw new org.xml.sax.SAXException(sb.toString());
        }

        if (multiParams == null) {
            paramValues[multiParamIndex] = null;
            IntrospectionUtils.callMethodN(target, methodName, paramValues,
                    paramTypes);
            return;
        }

        for (int j = 0; j < multiParams.size(); j++) {
            Object param = multiParams.get(j);
            if(param == null || (param instanceof String
                    && !String.class.isAssignableFrom(paramTypes[multiParamIndex]))) {
                paramValues[multiParamIndex] =
                    IntrospectionUtils.convert((String) param, paramTypes[multiParamIndex]);
            } else {
                paramValues[multiParamIndex] = param;
            }
            IntrospectionUtils.callMethodN(target, methodName, paramValues,
                    paramTypes);
        }
    }
}



/**
 * 用于检查是否必须加载注解的规则.
 */
final class IgnoreAnnotationsRule extends Rule {

    public IgnoreAnnotationsRule() {
        // NO-OP
    }

    @Override
    public void begin(String namespace, String name, Attributes attributes)
        throws Exception {
        WebXml webxml = (WebXml) digester.peek(digester.getCount() - 1);
        String value = attributes.getValue("metadata-complete");
        if ("true".equals(value)) {
            webxml.setMetadataComplete(true);
        } else if ("false".equals(value)) {
            webxml.setMetadataComplete(false);
        }
        if (digester.getLogger().isDebugEnabled()) {
            digester.getLogger().debug
                (webxml.getClass().getName() + ".setMetadataComplete( " +
                        webxml.isMetadataComplete() + ")");
        }
    }

}

/**
 * 记录要解析的web.xml的规范版本的规则
 */
final class VersionRule extends Rule {

    public VersionRule() {
        // NO-OP
    }

    @Override
    public void begin(String namespace, String name, Attributes attributes)
        throws Exception {
        WebXml webxml = (WebXml) digester.peek(digester.getCount() - 1);
        webxml.setVersion(attributes.getValue("version"));

        if (digester.getLogger().isDebugEnabled()) {
            digester.getLogger().debug
                (webxml.getClass().getName() + ".setVersion( " +
                        webxml.getVersion() + ")");
        }
    }
}


/**
 * 确保仅存在单个名称元素的规则.
 */
final class NameRule extends Rule {

    boolean isNameSet = false;

    public NameRule() {
        // NO-OP
    }

    @Override
    public void begin(String namespace, String name, Attributes attributes)
        throws Exception {
        if (isNameSet){
            throw new IllegalArgumentException(WebRuleSet.sm.getString(
                    "webRuleSet.nameCount"));
        }
        isNameSet = true;
    }

    @Override
    public void body(String namespace, String name, String text)
            throws Exception {
        super.body(namespace, name, text);
        ((WebXml) digester.peek()).setName(text);
    }
}


/**
 * 如果为片段配置了绝对排序则记录警告, 如果配置了多个绝对排序则失败.
 */
final class AbsoluteOrderingRule extends Rule {

    boolean isAbsoluteOrderingSet = false;
    private final boolean fragment;

    public AbsoluteOrderingRule(boolean fragment) {
        this.fragment = fragment;
    }

    @Override
    public void begin(String namespace, String name, Attributes attributes)
            throws Exception {
        if (fragment) {
            digester.getLogger().warn(
                    WebRuleSet.sm.getString("webRuleSet.absoluteOrdering"));
        }
        if (isAbsoluteOrderingSet) {
            throw new IllegalArgumentException(WebRuleSet.sm.getString(
                    "webRuleSet.absoluteOrderingCount"));
        } else {
            isAbsoluteOrderingSet = true;
            WebXml webXml = (WebXml) digester.peek();
            webXml.createAbsoluteOrdering();
            if (digester.getLogger().isDebugEnabled()) {
                digester.getLogger().debug(
                        webXml.getClass().getName() + ".setAbsoluteOrdering()");
            }
        }
    }
}

/**
 * 如果配置了相对排序，则记录警告的规则.
 */
final class RelativeOrderingRule extends Rule {

    boolean isRelativeOrderingSet = false;
    private final boolean fragment;

    public RelativeOrderingRule(boolean fragment) {
        this.fragment = fragment;
    }

    @Override
    public void begin(String namespace, String name, Attributes attributes)
            throws Exception {
        if (!fragment) {
            digester.getLogger().warn(
                    WebRuleSet.sm.getString("webRuleSet.relativeOrdering"));
        }
        if (isRelativeOrderingSet) {
            throw new IllegalArgumentException(WebRuleSet.sm.getString(
                    "webRuleSet.relativeOrderingCount"));
        } else {
            isRelativeOrderingSet = true;
        }
    }
}

/**
 * 在ContextHandler上设置soap标头的规则.
 */
final class SoapHeaderRule extends Rule {

    public SoapHeaderRule() {
        // NO-OP
    }

    /**
     * 处理此元素的正文主体.
     *
     * @param namespace 匹配元素的命名空间URI; 如果解析器不支持命名空间或元素没有命名空间，则为空字符串
     * @param name  如果解析器是命名空间感知的本地名称, 或者只是元素名称
     * @param text  这个元素的正文主体
     */
    @Override
    public void body(String namespace, String name, String text)
            throws Exception {
        String namespaceuri = null;
        String localpart = text;
        int colon = text.indexOf(':');
        if (colon >= 0) {
            String prefix = text.substring(0,colon);
            namespaceuri = digester.findNamespaceURI(prefix);
            localpart = text.substring(colon+1);
        }
        ContextHandler contextHandler = (ContextHandler)digester.peek();
        contextHandler.addSoapHeaders(localpart,namespaceuri);
    }
}

/**
 * 在ContextService上设置服务qname的规则.
 */
final class ServiceQnameRule extends Rule {

    public ServiceQnameRule() {
        // NO-OP
    }

    /**
     * 处理此元素的正文主体.
     *
     * @param namespace 匹配元素的命名空间URI; 如果解析器不支持命名空间或元素没有命名空间，则为空字符串
     * @param name 如果解析器是命名空间感知的本地名称, 或者只是元素名称
     * @param text 这个元素的正文主体
     */
    @Override
    public void body(String namespace, String name, String text)
            throws Exception {
        String namespaceuri = null;
        String localpart = text;
        int colon = text.indexOf(':');
        if (colon >= 0) {
            String prefix = text.substring(0,colon);
            namespaceuri = digester.findNamespaceURI(prefix);
            localpart = text.substring(colon+1);
        }
        ContextService contextService = (ContextService)digester.peek();
        contextService.setServiceqnameLocalpart(localpart);
        contextService.setServiceqnameNamespaceURI(namespaceuri);
    }

}

/**
 * 检查taglib元素是否在正确位置的规则.
 */
final class TaglibLocationRule extends Rule {

    final boolean isServlet24OrLater;

    public TaglibLocationRule(boolean isServlet24OrLater) {
        this.isServlet24OrLater = isServlet24OrLater;
    }

    @Override
    public void begin(String namespace, String name, Attributes attributes)
            throws Exception {
        WebXml webXml = (WebXml) digester.peek(digester.getCount() - 1);
        // 如果我们有公共ID，则不是2.4或更高版本的webapp
        boolean havePublicId = (webXml.getPublicId() != null);
        // havePublicId 和 isServlet24OrLater 应该是互斥的
        if (havePublicId == isServlet24OrLater) {
            throw new IllegalArgumentException(
                    "taglib definition not consistent with specification version");
        }
    }
}

/**
 * 在ResourceBase上设置映射名称的规则.
 */
final class MappedNameRule extends Rule {

    public MappedNameRule() {
        // NO-OP
    }

    /**
     * 处理此元素的正文主体.
     *
     * @param namespace 匹配元素的命名空间URI; 如果解析器不支持命名空间或元素没有命名空间，则为空字符串
     * @param name 如果解析器是命名空间感知的本地名称, 或者只是元素名称
     * @param text 这个元素的正文主体
     */
    @Override
    public void body(String namespace, String name, String text)
            throws Exception {
        ResourceBase resourceBase = (ResourceBase) digester.peek();
        resourceBase.setProperty("mappedName", text.trim());
    }
}

/**
 * 如果为每个类配置了多个post构造函数或pre destroy方法，则失败.
 */
final class LifecycleCallbackRule extends CallMethodRule {

    private final boolean postConstruct;

    public LifecycleCallbackRule(String methodName, int paramCount,
            boolean postConstruct) {
        super(methodName, paramCount);
        this.postConstruct = postConstruct;
    }

    @Override
    public void end(String namespace, String name) throws Exception {
        Object[] params = (Object[]) digester.peekParams();
        if (params != null && params.length == 2) {
            WebXml webXml = (WebXml) digester.peek();
            if (postConstruct) {
                if (webXml.getPostConstructMethods().containsKey(params[0])) {
                    throw new IllegalArgumentException(WebRuleSet.sm.getString(
                            "webRuleSet.postconstruct.duplicate", params[0]));
                }
            } else {
                if (webXml.getPreDestroyMethods().containsKey(params[0])) {
                    throw new IllegalArgumentException(WebRuleSet.sm.getString(
                            "webRuleSet.predestroy.duplicate", params[0]));
                }
            }
        }
        super.end(namespace, name);
    }
}

final class SetOverrideRule extends Rule {

    public SetOverrideRule() {
        // no-op
    }

    @Override
    public void begin(String namespace, String name, Attributes attributes) throws Exception {
        ContextEnvironment envEntry = (ContextEnvironment) digester.peek();
        envEntry.setOverride(false);
        if (digester.getLogger().isDebugEnabled()) {
            digester.getLogger().debug(envEntry.getClass().getName() + ".setOverride(false)");
        }
    }
}
