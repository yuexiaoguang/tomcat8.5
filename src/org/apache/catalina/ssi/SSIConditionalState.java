package org.apache.catalina.ssi;


/**
 * 这个类被 SSIMediator 和 SSIConditional 使用跟踪处理嵌套条件命令所必需的状态信息 ( if, elif, else, endif ).
 */
class SSIConditionalState {
    /**
     * 设置为 true， 如果当前条件已经完成, i.e.: 采取了一个分支.
     */
    boolean branchTaken = false;
    /**
     * 计数嵌套的假分支的数目
     */
    int nestingCount = 0;
    /**
     * 设置为 true， 如果只有条件命令 ( if, elif, else, endif )应处理.
     */
    boolean processConditionalCommandsOnly = false;
}