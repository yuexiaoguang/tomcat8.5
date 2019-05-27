package org.apache.catalina.ssi;


import java.io.PrintWriter;
/**
 * 实现了服务器端 #exec 命令
 */
public final class SSIConfig implements SSICommand {
    @Override
    public long process(SSIMediator ssiMediator, String commandName,
            String[] paramNames, String[] paramValues, PrintWriter writer) {
        for (int i = 0; i < paramNames.length; i++) {
            String paramName = paramNames[i];
            String paramValue = paramValues[i];
            String substitutedValue = ssiMediator
                    .substituteVariables(paramValue);
            if (paramName.equalsIgnoreCase("errmsg")) {
                ssiMediator.setConfigErrMsg(substitutedValue);
            } else if (paramName.equalsIgnoreCase("sizefmt")) {
                ssiMediator.setConfigSizeFmt(substitutedValue);
            } else if (paramName.equalsIgnoreCase("timefmt")) {
                ssiMediator.setConfigTimeFmt(substitutedValue);
            } else {
                ssiMediator.log("#config--Invalid attribute: " + paramName);
                //每次都需要取这个值, 在循环中它会改变
                String configErrMsg = ssiMediator.getConfigErrMsg();
                writer.write(configErrMsg);
            }
        }
        // 设置配置选项并不能真正改变页面
        return 0;
    }
}