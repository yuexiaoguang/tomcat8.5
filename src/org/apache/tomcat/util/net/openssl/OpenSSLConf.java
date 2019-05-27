package org.apache.tomcat.util.net.openssl;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.jni.SSLConf;
import org.apache.tomcat.util.res.StringManager;

public class OpenSSLConf implements Serializable {

    private static final long serialVersionUID = 1L;

    private static final Log log = LogFactory.getLog(OpenSSLConf.class);
    private static final StringManager sm = StringManager.getManager(OpenSSLConf.class);

    private final List<OpenSSLConfCmd> commands = new ArrayList<>();

    public void addCmd(OpenSSLConfCmd cmd) {
        commands.add(cmd);
    }

    public List<OpenSSLConfCmd> getCommands() {
        return commands;
    }

    public boolean check(long cctx) throws Exception {
        boolean result = true;
        OpenSSLConfCmd cmd;
        String name;
        String value;
        int rc;
        for (int i = 0; i < commands.size(); i++) {
            cmd = commands.get(i);
            name = cmd.getName();
            value = cmd.getValue();
            if (name == null) {
                log.error(sm.getString("opensslconf.noCommandName", value));
                result = false;
                continue;
            }
            if (log.isDebugEnabled()) {
                log.debug(sm.getString("opensslconf.checkCommand", name, value));
            }
            try {
                rc = SSLConf.check(cctx, name, value);
            } catch (Exception e) {
                log.error(sm.getString("opensslconf.checkFailed"));
                return false;
            }
            if (rc <= 0) {
                log.error(sm.getString("opensslconf.failedCommand", name, value,
                        Integer.toString(rc)));
                result = false;
            } else if (log.isDebugEnabled()) {
                log.debug(sm.getString("opensslconf.resultCommand", name, value,
                        Integer.toString(rc)));
            }
        }
        if (!result) {
            log.error(sm.getString("opensslconf.checkFailed"));
        }
        return result;
    }

    public boolean apply(long cctx, long ctx) throws Exception {
        boolean result = true;
        SSLConf.assign(cctx, ctx);
        OpenSSLConfCmd cmd;
        String name;
        String value;
        int rc;
        for (int i = 0; i < commands.size(); i++) {
            cmd = commands.get(i);
            name = cmd.getName();
            value = cmd.getValue();
            if (name == null) {
                log.error(sm.getString("opensslconf.noCommandName", value));
                result = false;
                continue;
            }
            if (log.isDebugEnabled()) {
                log.debug(sm.getString("opensslconf.applyCommand", name, value));
            }
            try {
                rc = SSLConf.apply(cctx, name, value);
            } catch (Exception e) {
                log.error(sm.getString("opensslconf.applyFailed"));
                return false;
            }
            if (rc <= 0) {
                log.error(sm.getString("opensslconf.failedCommand", name, value,
                        Integer.toString(rc)));
                result = false;
            } else if (log.isDebugEnabled()) {
                log.debug(sm.getString("opensslconf.resultCommand", name, value,
                        Integer.toString(rc)));
            }
        }
        rc = SSLConf.finish(cctx);
        if (rc <= 0) {
            log.error(sm.getString("opensslconf.finishFailed", Integer.toString(rc)));
            result = false;
        }
        if (!result) {
            log.error(sm.getString("opensslconf.applyFailed"));
        }
        return result;
    }
}
