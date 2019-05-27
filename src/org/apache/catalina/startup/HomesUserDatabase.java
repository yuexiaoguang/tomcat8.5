package org.apache.catalina.startup;


import java.io.File;
import java.util.Enumeration;
import java.util.Hashtable;


/**
 * <strong>UserDatabase</code>接口的实现类，
 * 构造器中指定路径名的目录将作为这些用户的"home"目录.
 */
public final class HomesUserDatabase
    implements UserDatabase {


    // --------------------------------------------------------- Constructors


    public HomesUserDatabase() {
        super();
    }


    // --------------------------------------------------- Instance Variables


    /**
     * 所有定义用户的主目录集合, 使用username作为key.
     */
    private final Hashtable<String,String> homes = new Hashtable<>();


    /**
     * UserConfig 监听器
     */
    private UserConfig userConfig = null;


    // ----------------------------------------------------------- Properties


    /**
     * 返回UserConfig监听器
     */
    @Override
    public UserConfig getUserConfig() {
        return (this.userConfig);
    }


    /**
     * 设置UserConfig监听器
     *
     * @param userConfig The new UserConfig listener
     */
    @Override
    public void setUserConfig(UserConfig userConfig) {
        this.userConfig = userConfig;
        init();
    }


    // ------------------------------------------------------- Public Methods


    /**
     * 返回一个绝对路径名为指定用户的主目录.
     *
     * @param user 应检索主目录的用户
     */
    @Override
    public String getHome(String user) {
        return homes.get(user);
    }


    /**
     * 返回此服务器上的用户名枚举定义
     */
    @Override
    public Enumeration<String> getUsers() {
        return (homes.keys());
    }


    // ------------------------------------------------------ Private Methods


    /**
     * 初始化用户集合和主目录.
     */
    private void init() {

        String homeBase = userConfig.getHomeBase();
        File homeBaseDir = new File(homeBase);
        if (!homeBaseDir.exists() || !homeBaseDir.isDirectory())
            return;
        String homeBaseFiles[] = homeBaseDir.list();
        if (homeBaseFiles == null) {
            return;
        }

        for (int i = 0; i < homeBaseFiles.length; i++) {
            File homeDir = new File(homeBaseDir, homeBaseFiles[i]);
            if (!homeDir.isDirectory() || !homeDir.canRead())
                continue;
            homes.put(homeBaseFiles[i], homeDir.toString());
        }
    }
}
