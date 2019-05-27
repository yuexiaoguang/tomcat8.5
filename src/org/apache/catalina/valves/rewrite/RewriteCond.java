package org.apache.catalina.valves.rewrite;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RewriteCond {

    public abstract static class Condition {
        public abstract boolean evaluate(String value, Resolver resolver);
    }

    public static class PatternCondition extends Condition {
        public Pattern pattern;
        public Matcher matcher = null;

        @Override
        public boolean evaluate(String value, Resolver resolver) {
            Matcher m = pattern.matcher(value);
            if (m.matches()) {
                matcher = m;
                return true;
            } else {
                return false;
            }
        }
    }

    public static class LexicalCondition extends Condition {
        /**
         * -1: &lt;
         * 0: =
         * 1: &gt;
         */
        public int type = 0;
        public String condition;

        @Override
        public boolean evaluate(String value, Resolver resolver) {
            int result = value.compareTo(condition);
            switch (type) {
            case -1:
                return (result < 0);
            case 0:
                return (result == 0);
            case 1:
                return (result > 0);
            default:
                return false;
            }

        }
    }

    public static class ResourceCondition extends Condition {
        /**
         * 0: -d (目录 ?)
         * 1: -f (文件 ?)
         * 2: -s (有大小的普通文件 ?)
         */
        public int type = 0;

        @Override
        public boolean evaluate(String value, Resolver resolver) {
            return resolver.resolveResource(type, value);
        }
    }

    protected String testString = null;
    protected String condPattern = null;

    public String getCondPattern() {
        return condPattern;
    }

    public void setCondPattern(String condPattern) {
        this.condPattern = condPattern;
    }

    public String getTestString() {
        return testString;
    }

    public void setTestString(String testString) {
        this.testString = testString;
    }

    public void parse(Map<String, RewriteMap> maps) {
        test = new Substitution();
        test.setSub(testString);
        test.parse(maps);
        if (condPattern.startsWith("!")) {
            positive = false;
            condPattern = condPattern.substring(1);
        }
        if (condPattern.startsWith("<")) {
            LexicalCondition condition = new LexicalCondition();
            condition.type = -1;
            condition.condition = condPattern.substring(1);
        } else if (condPattern.startsWith(">")) {
            LexicalCondition condition = new LexicalCondition();
            condition.type = 1;
            condition.condition = condPattern.substring(1);
        } else if (condPattern.startsWith("=")) {
            LexicalCondition condition = new LexicalCondition();
            condition.type = 0;
            condition.condition = condPattern.substring(1);
        } else if (condPattern.equals("-d")) {
            ResourceCondition ncondition = new ResourceCondition();
            ncondition.type = 0;
        } else if (condPattern.equals("-f")) {
            ResourceCondition ncondition = new ResourceCondition();
            ncondition.type = 1;
        } else if (condPattern.equals("-s")) {
            ResourceCondition ncondition = new ResourceCondition();
            ncondition.type = 2;
        } else {
            PatternCondition condition = new PatternCondition();
            int flags = 0;
            if (isNocase()) {
                flags |= Pattern.CASE_INSENSITIVE;
            }
            condition.pattern = Pattern.compile(condPattern, flags);
        }
    }

    public Matcher getMatcher() {
        Object condition = this.condition.get();
        if (condition instanceof PatternCondition) {
            return ((PatternCondition) condition).matcher;
        }
        return null;
    }

    @Override
    public String toString() {
        // FIXME: Add flags if possible
        return "RewriteCond " + testString + " " + condPattern;
    }


    protected boolean positive = true;

    protected Substitution test = null;

    protected ThreadLocal<Condition> condition = new ThreadLocal<>();

    /**
     * 忽略大小写, i.e., 即'A-Z' 和 'a-z'没有区别, 都在扩展的 TestString 和 CondPattern中.
     * 此标志仅用于比较 TestString 和 CondPattern. 它对文件系统和子请求检查没有影响.
     */
    public boolean nocase = false;

    /**
     * 使用这个组合规则条件, 使用本地 OR, 而不是隐式的 AND.
     */
    public boolean ornext = false;

    /**
     * 基于上下文评估条件
     *
     * @param rule 对应匹配的规则
     * @param cond 最后匹配的条件
     * @param resolver 属性解析器
     * @return <code>true</code>如果条件匹配
     */
    public boolean evaluate(Matcher rule, Matcher cond, Resolver resolver) {
        String value = test.evaluate(rule, cond, resolver);
        Condition condition = this.condition.get();
        if (condition == null) {
            if (condPattern.startsWith("<")) {
                LexicalCondition ncondition = new LexicalCondition();
                ncondition.type = -1;
                ncondition.condition = condPattern.substring(1);
                condition = ncondition;
            } else if (condPattern.startsWith(">")) {
                LexicalCondition ncondition = new LexicalCondition();
                ncondition.type = 1;
                ncondition.condition = condPattern.substring(1);
                condition = ncondition;
            } else if (condPattern.startsWith("=")) {
                LexicalCondition ncondition = new LexicalCondition();
                ncondition.type = 0;
                ncondition.condition = condPattern.substring(1);
                condition = ncondition;
            } else if (condPattern.equals("-d")) {
                ResourceCondition ncondition = new ResourceCondition();
                ncondition.type = 0;
                condition = ncondition;
            } else if (condPattern.equals("-f")) {
                ResourceCondition ncondition = new ResourceCondition();
                ncondition.type = 1;
                condition = ncondition;
            } else if (condPattern.equals("-s")) {
                ResourceCondition ncondition = new ResourceCondition();
                ncondition.type = 2;
                condition = ncondition;
            } else {
                PatternCondition ncondition = new PatternCondition();
                int flags = 0;
                if (isNocase()) {
                    flags |= Pattern.CASE_INSENSITIVE;
                }
                ncondition.pattern = Pattern.compile(condPattern, flags);
                condition = ncondition;
            }
            this.condition.set(condition);
        }
        if (positive) {
            return condition.evaluate(value, resolver);
        } else {
            return !condition.evaluate(value, resolver);
        }
    }

    public boolean isNocase() {
        return nocase;
    }

    public void setNocase(boolean nocase) {
        this.nocase = nocase;
    }

    public boolean isOrnext() {
        return ornext;
    }

    public void setOrnext(boolean ornext) {
        this.ornext = ornext;
    }

    public boolean isPositive() {
        return positive;
    }

    public void setPositive(boolean positive) {
        this.positive = positive;
    }
}
