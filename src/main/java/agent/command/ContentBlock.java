package agent.command;

/**
 * 消息内容块 - 对应 JSON 中 content 数组的每个元素
 */
public class ContentBlock {

    public enum BlockType {
        /** 普通文本 */
        TEXT
    }

    private final BlockType type;
    private final String text;

    public ContentBlock(String text) {
        this.type = BlockType.TEXT;
        this.text = text;
    }

    public BlockType getType() {
        return type;
    }

    public String getText() {
        return text;
    }

    @Override
    public String toString() {
        return "ContentBlock{type=" + type + ", text='" + text + "'}";
    }
}
