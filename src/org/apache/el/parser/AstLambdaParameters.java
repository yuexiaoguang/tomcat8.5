package org.apache.el.parser;

public class AstLambdaParameters extends SimpleNode {

    public AstLambdaParameters(int id) {
        super(id);
    }

    @Override
    public String toString() {
        // 纯粹用于调试目的. 当然不是有效的. 不要在'real' 模式下调用.
        StringBuilder result = new StringBuilder();
        result.append('(');
        if (children != null) {
            for (Node n : children) {
                result.append(n.toString());
                result.append(',');
            }
        }
        result.append(")->");
        return result.toString();
    }

}
/* JavaCC - OriginalChecksum=a8c1609257dac59e41c43d6ed91072c6 (do not edit this line) */
