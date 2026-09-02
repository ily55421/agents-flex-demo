package com.agentsflex.showcase.model;

/**
 * 承载用于恢复工具审批 Suspension 的人工决策。
 */
public class ApprovalRequest {

    private boolean approved;
    private String reason;
    private String approver = "showcase-user";

    /**
     * @return {@code true} 表示批准执行待处理副作用工具，否则表示拒绝
     */
    public boolean isApproved() {
        return approved;
    }

    /**
     * @param approved 人工审批结果，决定生成 APPROVE_TOOL 还是 REJECT_TOOL 命令
     */
    public void setApproved(boolean approved) {
        this.approved = approved;
    }

    /**
     * @return 拒绝原因；批准时允许为空
     */
    public String getReason() {
        return reason;
    }

    /**
     * @param reason 拒绝工具执行时回传给 Agent 的说明
     */
    public void setReason(String reason) {
        this.reason = reason;
    }

    /**
     * @return 审批人标识，用于写入 ResumeCommand metadata
     */
    public String getApprover() {
        return approver;
    }

    /**
     * @param approver 审批人标识；Demo 默认使用 showcase-user
     */
    public void setApprover(String approver) {
        this.approver = approver;
    }
}
