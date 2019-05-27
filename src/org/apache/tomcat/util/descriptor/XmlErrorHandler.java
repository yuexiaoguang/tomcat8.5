package org.apache.tomcat.util.descriptor;

import java.util.ArrayList;
import java.util.List;

import org.apache.juli.logging.Log;
import org.apache.tomcat.util.res.StringManager;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

public class XmlErrorHandler implements ErrorHandler {

    private static final StringManager sm =
        StringManager.getManager(Constants.PACKAGE_NAME);

    private final List<SAXParseException> errors = new ArrayList<>();

    private final List<SAXParseException> warnings = new ArrayList<>();

    @Override
    public void error(SAXParseException exception) throws SAXException {
        // 收集非致命错误
        errors.add(exception);
    }

    @Override
    public void fatalError(SAXParseException exception) throws SAXException {
        // 重新抛出致命错误
        throw exception;
    }

    @Override
    public void warning(SAXParseException exception) throws SAXException {
        // 收集警告
        warnings.add(exception);
    }

    public List<SAXParseException> getErrors() {
        // 仅供内部使用 - 不要担心不变性
        return errors;
    }

    public List<SAXParseException> getWarnings() {
        // 仅供内部使用 - 不要担心不变性
        return warnings;
    }

    public void logFindings(Log log, String source) {
        for (SAXParseException e : getWarnings()) {
            log.warn(sm.getString(
                    "xmlErrorHandler.warning", e.getMessage(), source));
        }
        for (SAXParseException e : getErrors()) {
            log.warn(sm.getString(
                    "xmlErrorHandler.error", e.getMessage(), source));
        }
    }
}
