package org.apache.catalina.startup;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.Enumeration;
import java.util.Hashtable;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.naming.StringManager;

/**
 * 在Unix系统中处理<code>/etc/passwd</code>文件.
 */
public final class PasswdUserDatabase implements UserDatabase {

    private static final Log log = LogFactory.getLog(PasswdUserDatabase.class);
    private static final StringManager sm = StringManager.getManager(PasswdUserDatabase.class);

    /**
     * UNIX密码文件的路径.
     */
    private static final String PASSWORD_FILE = "/etc/passwd";


    /**
     * 所有定义用户的主目录集合, 使用用户名作为key.
     */
    private final Hashtable<String,String> homes = new Hashtable<>();


    /**
     * UserConfig监听器
     */
    private UserConfig userConfig = null;


    /**
     * 返回UserConfig监听器.
     */
    @Override
    public UserConfig getUserConfig() {
        return userConfig;
    }


    /**
     * 设置UserConfig监听器.
     *
     * @param userConfig The new UserConfig listener
     */
    @Override
    public void setUserConfig(UserConfig userConfig) {
        this.userConfig = userConfig;
        init();
    }


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
     * 返回此服务器上的用户名枚举.
     */
    @Override
    public Enumeration<String> getUsers() {
        return homes.keys();
    }


    /**
     * 初始化用户集和主目录.
     */
    private void init() {
        try (BufferedReader reader = new BufferedReader(new FileReader(PASSWORD_FILE))) {
            String line = reader.readLine();
            while (line != null) {
                String tokens[] = line.split(":");
                // Need non-zero 1st and 6th tokens
                if (tokens.length > 5 && tokens[0].length() > 0 && tokens[5].length() > 0) {
                    // 添加此用户和相应的目录
                    homes.put(tokens[0], tokens[5]);
                }
                line = reader.readLine();
            }
        } catch (Exception e) {
            log.warn(sm.getString("passwdUserDatabase.readFail"), e);
        }
    }
}
