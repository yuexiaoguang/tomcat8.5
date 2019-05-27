package org.apache.catalina.ha;


import org.apache.tomcat.util.digester.Digester;
import org.apache.tomcat.util.digester.RuleSetBase;


/**
 * <p>处理集群定义元素的内容的<strong>RuleSet</strong>.</p>
 */
@SuppressWarnings("deprecation")
public class ClusterRuleSet extends RuleSetBase {


    // ----------------------------------------------------- Instance Variables


    /**
     * 用于识别元素的匹配模式前缀.
     */
    protected final String prefix;


    // ------------------------------------------------------------ Constructor


    public ClusterRuleSet() {
        this("");
    }


    /**
     * @param prefix 匹配模式规则的前缀(包括尾随斜杠字符)
     */
    public ClusterRuleSet(String prefix) {
        super();
        this.prefix = prefix;
    }


    // --------------------------------------------------------- Public Methods


    /**
     * <p>添加这个RuleSet中定义的Rule 实例集合到指定的<code>Digester</code>实例, 将它们与命名空间URI管理.
     * 这个方法只能被Digester实例调用.</p>
     *
     * @param digester 应该添加Rule实例的Digester实例.
     */
    @Override
    public void addRuleInstances(Digester digester) {
        //Cluster配置开始
        digester.addObjectCreate(prefix + "Manager",
                                 null, // 必须在元素中指定
                                 "className");
        digester.addSetProperties(prefix + "Manager");
        digester.addSetNext(prefix + "Manager",
                            "setManagerTemplate",
                            "org.apache.catalina.ha.ClusterManager");
        digester.addObjectCreate(prefix + "Manager/SessionIdGenerator",
                "org.apache.catalina.util.StandardSessionIdGenerator",
                "className");
        digester.addSetProperties(prefix + "Manager/SessionIdGenerator");
        digester.addSetNext(prefix + "Manager/SessionIdGenerator",
               "setSessionIdGenerator",
               "org.apache.catalina.SessionIdGenerator");

        digester.addObjectCreate(prefix + "Channel",
                                 null, // 必须在元素中指定
                                 "className");
        digester.addSetProperties(prefix + "Channel");
        digester.addSetNext(prefix + "Channel",
                            "setChannel",
                            "org.apache.catalina.tribes.Channel");


        String channelPrefix = prefix + "Channel/";

        //channel properties
        digester.addObjectCreate(channelPrefix + "Membership",
                                 null, // 必须在元素中指定
                                 "className");
        digester.addSetProperties(channelPrefix + "Membership");
        digester.addSetNext(channelPrefix + "Membership",
                            "setMembershipService",
                            "org.apache.catalina.tribes.MembershipService");

        digester.addObjectCreate(channelPrefix + "MembershipListener",
                                 null, // 必须在元素中指定
                                 "className");
        digester.addSetProperties(channelPrefix + "MembershipListener");
        digester.addSetNext(channelPrefix + "MembershipListener",
                            "addMembershipListener",
                            "org.apache.catalina.tribes.MembershipListener");

        digester.addObjectCreate(channelPrefix + "Sender",
                                 null, // 必须在元素中指定
                                 "className");
        digester.addSetProperties(channelPrefix + "Sender");
        digester.addSetNext(channelPrefix + "Sender",
                            "setChannelSender",
                            "org.apache.catalina.tribes.ChannelSender");

        digester.addObjectCreate(channelPrefix + "Sender/Transport",
                                 null, // 必须在元素中指定
                                 "className");
        digester.addSetProperties(channelPrefix + "Sender/Transport");
        digester.addSetNext(channelPrefix + "Sender/Transport",
                            "setTransport",
                            "org.apache.catalina.tribes.transport.MultiPointSender");


        digester.addObjectCreate(channelPrefix + "Receiver",
                                 null, // 必须在元素中指定
                                 "className");
        digester.addSetProperties(channelPrefix + "Receiver");
        digester.addSetNext(channelPrefix + "Receiver",
                            "setChannelReceiver",
                            "org.apache.catalina.tribes.ChannelReceiver");

        digester.addObjectCreate(channelPrefix + "Interceptor",
                                 null, // 必须在元素中指定
                                 "className");
        digester.addSetProperties(channelPrefix + "Interceptor");
        digester.addSetNext(channelPrefix + "Interceptor",
                            "addInterceptor",
                            "org.apache.catalina.tribes.ChannelInterceptor");

        digester.addObjectCreate(channelPrefix + "Interceptor/LocalMember",
                                 null, // 必须在元素中指定
                                 "className");
        digester.addSetProperties(channelPrefix + "Interceptor/LocalMember");
        digester.addSetNext(channelPrefix + "Interceptor/LocalMember",
                            "setLocalMember",
                            "org.apache.catalina.tribes.Member");

        digester.addObjectCreate(channelPrefix + "Interceptor/Member",
                                 null, // 必须在元素中指定
                                 "className");
        digester.addSetProperties(channelPrefix + "Interceptor/Member");
        digester.addSetNext(channelPrefix + "Interceptor/Member",
                            "addStaticMember",
                            "org.apache.catalina.tribes.Member");

        digester.addObjectCreate(channelPrefix + "ChannelListener",
                                 null, // 必须在元素中指定
                                 "className");
        digester.addSetProperties(channelPrefix + "ChannelListener");
        digester.addSetNext(channelPrefix + "ChannelListener",
                            "addChannelListener",
                            "org.apache.catalina.tribes.ChannelListener");

        digester.addObjectCreate(prefix + "Valve",
                                 null, // 必须在元素中指定
                                 "className");
        digester.addSetProperties(prefix + "Valve");
        digester.addSetNext(prefix + "Valve",
                            "addValve",
                            "org.apache.catalina.Valve");

        digester.addObjectCreate(prefix + "Deployer",
                                 null, // 必须在元素中指定
                                 "className");
        digester.addSetProperties(prefix + "Deployer");
        digester.addSetNext(prefix + "Deployer",
                            "setClusterDeployer",
                            "org.apache.catalina.ha.ClusterDeployer");

        digester.addObjectCreate(prefix + "Listener",
                null, // 必须在元素中指定
                "className");
        digester.addSetProperties(prefix + "Listener");
        digester.addSetNext(prefix + "Listener",
                            "addLifecycleListener",
                            "org.apache.catalina.LifecycleListener");

        digester.addObjectCreate(prefix + "ClusterListener",
                null, // 必须在元素中指定
                "className");
        digester.addSetProperties(prefix + "ClusterListener");
        digester.addSetNext(prefix + "ClusterListener",
                            "addClusterListener",
                            "org.apache.catalina.ha.ClusterListener");
        //Cluster配置结束
    }

}
