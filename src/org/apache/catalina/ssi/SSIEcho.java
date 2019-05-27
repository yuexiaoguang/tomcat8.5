package org.apache.catalina.ssi;


import java.io.PrintWriter;
/**
 * 返回与所提供的服务器变量相关联的结果.
 */
public class SSIEcho implements SSICommand {
    protected static final String DEFAULT_ENCODING = "entity";
    protected static final String MISSING_VARIABLE_VALUE = "(none)";


    @Override
    public long process(SSIMediator ssiMediator, String commandName,
            String[] paramNames, String[] paramValues, PrintWriter writer) {
        String encoding = DEFAULT_ENCODING;
        String originalValue = null;
        String errorMessage = ssiMediator.getConfigErrMsg();
        for (int i = 0; i < paramNames.length; i++) {
            String paramName = paramNames[i];
            String paramValue = paramValues[i];
            if (paramName.equalsIgnoreCase("var")) {
                originalValue = paramValue;
            } else if (paramName.equalsIgnoreCase("encoding")) {
                if (isValidEncoding(paramValue)) {
                    encoding = paramValue;
                } else {
                    ssiMediator.log("#echo--Invalid encoding: " + paramValue);
                    writer.write(errorMessage);
                }
            } else {
                ssiMediator.log("#echo--Invalid attribute: " + paramName);
                writer.write(errorMessage);
            }
        }
        String variableValue = ssiMediator.getVariableValue(
                originalValue, encoding);
        if (variableValue == null) {
            variableValue = MISSING_VARIABLE_VALUE;
        }
        writer.write(variableValue);
        return System.currentTimeMillis();
    }


    protected boolean isValidEncoding(String encoding) {
        return encoding.equalsIgnoreCase("url")
                || encoding.equalsIgnoreCase("entity")
                || encoding.equalsIgnoreCase("none");
    }
}