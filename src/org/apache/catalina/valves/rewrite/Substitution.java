package org.apache.catalina.valves.rewrite;

import java.util.ArrayList;
import java.util.Map;
import java.util.regex.Matcher;

import org.apache.catalina.util.URLEncoder;

public class Substitution {

    public abstract class SubstitutionElement {
        public abstract String evaluate(Matcher rule, Matcher cond, Resolver resolver);
    }

    public class StaticElement extends SubstitutionElement {
        public String value;

        @Override
        public String evaluate(Matcher rule, Matcher cond, Resolver resolver) {
            return value;
        }

    }

    public class RewriteRuleBackReferenceElement extends SubstitutionElement {
        public int n;
        @Override
        public String evaluate(Matcher rule, Matcher cond, Resolver resolver) {
            String result = rule.group(n);
            if (result == null) {
                result = "";
            }
            if (escapeBackReferences) {
                // Note: 这应该与httpd的行为方式相一致.
                //       我们可能想考虑提供一个专用解码器，可以添加附加的安全字符，为用户提供更大的灵活性
                return URLEncoder.DEFAULT.encode(result, resolver.getUriCharset());
            } else {
                return result;
            }
        }
    }

    public class RewriteCondBackReferenceElement extends SubstitutionElement {
        public int n;
        @Override
        public String evaluate(Matcher rule, Matcher cond, Resolver resolver) {
            return (cond.group(n) == null ? "" : cond.group(n));
        }
    }

    public class ServerVariableElement extends SubstitutionElement {
        public String key;
        @Override
        public String evaluate(Matcher rule, Matcher cond, Resolver resolver) {
            return resolver.resolve(key);
        }
    }

    public class ServerVariableEnvElement extends SubstitutionElement {
        public String key;
        @Override
        public String evaluate(Matcher rule, Matcher cond, Resolver resolver) {
            return resolver.resolveEnv(key);
        }
    }

    public class ServerVariableSslElement extends SubstitutionElement {
        public String key;
        @Override
        public String evaluate(Matcher rule, Matcher cond, Resolver resolver) {
            return resolver.resolveSsl(key);
        }
    }

    public class ServerVariableHttpElement extends SubstitutionElement {
        public String key;
        @Override
        public String evaluate(Matcher rule, Matcher cond, Resolver resolver) {
            return resolver.resolveHttp(key);
        }
    }

    public class MapElement extends SubstitutionElement {
        public RewriteMap map = null;
        public String key;
        public String defaultValue = "";
        public int n;
        @Override
        public String evaluate(Matcher rule, Matcher cond, Resolver resolver) {
            String result = map.lookup(rule.group(n));
            if (result == null) {
                result = defaultValue;
            }
            return result;
        }
    }

    protected SubstitutionElement[] elements = null;

    protected String sub = null;
    public String getSub() { return sub; }
    public void setSub(String sub) { this.sub = sub; }

    private boolean escapeBackReferences;
    void setEscapeBackReferences(boolean escapeBackReferences) {
        this.escapeBackReferences = escapeBackReferences;
    }

    public void parse(Map<String, RewriteMap> maps) {

        ArrayList<SubstitutionElement> elements = new ArrayList<>();
        int pos = 0;
        int percentPos = 0;
        int dollarPos = 0;
        int backslashPos = 0;

        while (pos < sub.length()) {
            percentPos = sub.indexOf('%', pos);
            dollarPos = sub.indexOf('$', pos);
            backslashPos = sub.indexOf('\\', pos);
            if (percentPos == -1 && dollarPos == -1 && backslashPos == -1) {
                // Static text
                StaticElement newElement = new StaticElement();
                newElement.value = sub.substring(pos, sub.length());
                pos = sub.length();
                elements.add(newElement);
            } else if (isFirstPos(backslashPos, dollarPos, percentPos)) {
                if (backslashPos + 1 == sub.length()) {
                    throw new IllegalArgumentException(sub);
                }
                StaticElement newElement = new StaticElement();
                newElement.value = sub.substring(pos, backslashPos) + sub.substring(backslashPos + 1, backslashPos + 2);
                pos = backslashPos + 2;
                elements.add(newElement);
            } else if (isFirstPos(dollarPos, percentPos)) {
                // $: back reference to rule or map lookup
                if (dollarPos + 1 == sub.length()) {
                    throw new IllegalArgumentException(sub);
                }
                if (pos < dollarPos) {
                    // Static text
                    StaticElement newElement = new StaticElement();
                    newElement.value = sub.substring(pos, dollarPos);
                    pos = dollarPos;
                    elements.add(newElement);
                }
                if (Character.isDigit(sub.charAt(dollarPos + 1))) {
                    // $: back reference to rule
                    RewriteRuleBackReferenceElement newElement = new RewriteRuleBackReferenceElement();
                    newElement.n = Character.digit(sub.charAt(dollarPos + 1), 10);
                    pos = dollarPos + 2;
                    elements.add(newElement);
                } else if (sub.charAt(dollarPos + 1) == '{') {
                    // $: map lookup as ${mapname:key|default}
                    MapElement newElement = new MapElement();
                    int open = sub.indexOf('{', dollarPos);
                    int colon = sub.indexOf(':', dollarPos);
                    int def = sub.indexOf('|', dollarPos);
                    int close = sub.indexOf('}', dollarPos);
                    if (!(-1 < open && open < colon && colon < close)) {
                        throw new IllegalArgumentException(sub);
                    }
                    newElement.map = maps.get(sub.substring(open + 1, colon));
                    if (newElement.map == null) {
                        throw new IllegalArgumentException(sub + ": No map: " + sub.substring(open + 1, colon));
                    }
                    if (def > -1) {
                        if (!(colon < def && def < close)) {
                            throw new IllegalArgumentException(sub);
                        }
                        newElement.key = sub.substring(colon + 1, def);
                        newElement.defaultValue = sub.substring(def + 1, close);
                    } else {
                        newElement.key = sub.substring(colon + 1, close);
                    }
                    if (newElement.key.startsWith("$")) {
                        newElement.n = Integer.parseInt(newElement.key.substring(1));
                    }
                    pos = close + 1;
                    elements.add(newElement);
                } else {
                    throw new IllegalArgumentException(sub + ": missing digit or curly brace.");
                }
            } else {
                // %: back reference to condition or server variable
                if (percentPos + 1 == sub.length()) {
                    throw new IllegalArgumentException(sub);
                }
                if (pos < percentPos) {
                    // Static text
                    StaticElement newElement = new StaticElement();
                    newElement.value = sub.substring(pos, percentPos);
                    pos = percentPos;
                    elements.add(newElement);
                }
                if (Character.isDigit(sub.charAt(percentPos + 1))) {
                    // %: back reference to condition
                    RewriteCondBackReferenceElement newElement = new RewriteCondBackReferenceElement();
                    newElement.n = Character.digit(sub.charAt(percentPos + 1), 10);
                    pos = percentPos + 2;
                    elements.add(newElement);
                } else if (sub.charAt(percentPos + 1) == '{') {
                    // %: server variable as %{variable}
                    SubstitutionElement newElement = null;
                    int open = sub.indexOf('{', percentPos);
                    int colon = sub.indexOf(':', percentPos);
                    int close = sub.indexOf('}', percentPos);
                    if (!(-1 < open && open < close)) {
                        throw new IllegalArgumentException(sub);
                    }
                    if (colon > -1 && open < colon && colon < close) {
                        String type = sub.substring(open + 1, colon);
                        if (type.equals("ENV")) {
                            newElement = new ServerVariableEnvElement();
                            ((ServerVariableEnvElement) newElement).key = sub.substring(colon + 1, close);
                        } else if (type.equals("SSL")) {
                            newElement = new ServerVariableSslElement();
                            ((ServerVariableSslElement) newElement).key = sub.substring(colon + 1, close);
                        } else if (type.equals("HTTP")) {
                            newElement = new ServerVariableHttpElement();
                            ((ServerVariableHttpElement) newElement).key = sub.substring(colon + 1, close);
                        } else {
                            throw new IllegalArgumentException(sub + ": Bad type: " + type);
                        }
                    } else {
                        newElement = new ServerVariableElement();
                        ((ServerVariableElement) newElement).key = sub.substring(open + 1, close);
                    }
                    pos = close + 1;
                    elements.add(newElement);
                } else {
                    throw new IllegalArgumentException(sub + ": missing digit or curly brace.");
                }
            }
        }

        this.elements = elements.toArray(new SubstitutionElement[0]);

    }

    /**
     * 基于上下文的替代评估.
     * @param rule 对应的匹配的规则
     * @param cond 最后匹配的条件
     * @param resolver 属性解析器
     * @return 替换的结果
     */
    public String evaluate(Matcher rule, Matcher cond, Resolver resolver) {
        StringBuffer buf = new StringBuffer();
        for (int i = 0; i < elements.length; i++) {
            buf.append(elements[i].evaluate(rule, cond, resolver));
        }
        return buf.toString();
    }

    /**
     * 检查第一个 int 是否是非负, 并且小于任何非负的{@code others}指定的其它 int.
     *
     * @param testPos 要测试的整数
     * @param others 配对 {@code testPos}的整数列表. 任何负整数都将被忽略.
     * @return {@code true} 如果 {@code testPos} 是非负的, 并且小于任何给定的其它整数; 否则{@code false}
     */
    private boolean isFirstPos(int testPos, int... others) {
        if (testPos < 0) {
            return false;
        }
        for (int other : others) {
            if (other >= 0 && other < testPos) {
                return false;
            }
        }
        return true;
    }
}
