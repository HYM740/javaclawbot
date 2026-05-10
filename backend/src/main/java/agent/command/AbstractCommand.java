package agent.command;

import java.util.List;

/**
 * 命令基类 - 所有命令的抽象父类
 */
public abstract class AbstractCommand {

    protected final String name;
    protected final String message;
    protected final String args;
    protected String output;

    protected AbstractCommand(String name, String message, String args) {
        this.name = name;
        this.message = message;
        this.args = args;
    }

    /** 获取命令类型 */
    public abstract CommandType getType();

    /** 执行命令，返回输出 */
    public abstract String execute();

    /** 将命令自身的各个部分转为 ContentBlock 数组 */
    public abstract List<ContentBlock> toContentBlocks();

    // ── Getters ──

    public String getName() {
        return name;
    }

    public String getMessage() {
        return message;
    }

    public String getArgs() {
        return args;
    }

    public String getOutput() {
        return output;
    }

    public void setOutput(String output) {
        this.output = output;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{name='" + name + "', type=" + getType() + "}";
    }
}
