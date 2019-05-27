package org.apache.tomcat.util.descriptor.tld;

import java.util.HashMap;
import java.util.Map;

/**
 * XML描述符中的标签库验证器模型.
 */
public class ValidatorXml {
    private String validatorClass;
    private final Map<String, String> initParams = new HashMap<>();

    public String getValidatorClass() {
        return validatorClass;
    }

    public void setValidatorClass(String validatorClass) {
        this.validatorClass = validatorClass;
    }

    public void addInitParam(String name, String value) {
        initParams.put(name, value);
    }

    public Map<String, String> getInitParams() {
        return initParams;
    }
}
