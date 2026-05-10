package agent.command;

/**
 * 命令类型枚举
 */
public enum CommandType {
    /** 本地命令 - 无需与 LLM 交互 */
    LOCAL,
    /** 技能命令 - 需要与 LLM 交互 */
    SKILL
}
