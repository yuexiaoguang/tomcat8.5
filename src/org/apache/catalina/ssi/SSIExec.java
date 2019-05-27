package org.apache.catalina.ssi;


import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;

import org.apache.catalina.util.IOTools;
/**
 * 实现了服务器端 #exec 命令
 */
public class SSIExec implements SSICommand {
    protected final SSIInclude ssiInclude = new SSIInclude();
    protected static final int BUFFER_SIZE = 1024;


    @Override
    public long process(SSIMediator ssiMediator, String commandName,
            String[] paramNames, String[] paramValues, PrintWriter writer) {
        long lastModified = 0;
        String configErrMsg = ssiMediator.getConfigErrMsg();
        String paramName = paramNames[0];
        String paramValue = paramValues[0];
        String substitutedValue = ssiMediator.substituteVariables(paramValue);
        if (paramName.equalsIgnoreCase("cgi")) {
            lastModified = ssiInclude.process(ssiMediator, "include",
                    new String[]{"virtual"}, new String[]{substitutedValue},
                    writer);
        } else if (paramName.equalsIgnoreCase("cmd")) {
            boolean foundProgram = false;
            try {
                Runtime rt = Runtime.getRuntime();
                Process proc = rt.exec(substitutedValue);
                foundProgram = true;
                BufferedReader stdOutReader = new BufferedReader(
                        new InputStreamReader(proc.getInputStream()));
                BufferedReader stdErrReader = new BufferedReader(
                        new InputStreamReader(proc.getErrorStream()));
                char[] buf = new char[BUFFER_SIZE];
                IOTools.flow(stdErrReader, writer, buf);
                IOTools.flow(stdOutReader, writer, buf);
                proc.waitFor();
                lastModified = System.currentTimeMillis();
            } catch (InterruptedException e) {
                ssiMediator.log("Couldn't exec file: " + substitutedValue, e);
                writer.write(configErrMsg);
            } catch (IOException e) {
                if (!foundProgram) {
                    //apache doesn't output an error message if it can't find
                    // a program
                }
                ssiMediator.log("Couldn't exec file: " + substitutedValue, e);
            }
        }
        return lastModified;
    }
}