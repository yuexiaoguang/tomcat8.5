package org.apache.catalina.ssi;


import java.io.PrintWriter;
import java.util.Collection;
import java.util.Iterator;
/**
 * 实现服务器端 #printenv 命令
 */
public class SSIPrintenv implements SSICommand {
    /**
     * @see SSICommand
     */
    @Override
    public long process(SSIMediator ssiMediator, String commandName,
            String[] paramNames, String[] paramValues, PrintWriter writer) {
        long lastModified = 0;
        //any arguments should produce an error
        if (paramNames.length > 0) {
            String errorMessage = ssiMediator.getConfigErrMsg();
            writer.write(errorMessage);
        } else {
            Collection<String> variableNames = ssiMediator.getVariableNames();
            Iterator<String> iter = variableNames.iterator();
            while (iter.hasNext()) {
                String variableName = iter.next();
                String variableValue = ssiMediator
                        .getVariableValue(variableName);
                //This shouldn't happen, since all the variable names must
                // have values
                if (variableValue == null) {
                    variableValue = "(none)";
                }
                writer.write(variableName);
                writer.write('=');
                writer.write(variableValue);
                writer.write('\n');
                lastModified = System.currentTimeMillis();
            }
        }
        return lastModified;
    }
}
