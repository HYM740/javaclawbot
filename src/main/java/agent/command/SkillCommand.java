package agent.command;

import lombok.Getter;
import skills.SkillsLoader;

import java.util.ArrayList;
import java.util.List;

public class SkillCommand extends AbstractCommand {

    @Getter
    private final String skillDesc;

    public SkillCommand(String name, String message, SkillsLoader loader) {
        super(name, message, "");
        if (name.startsWith("/")) {
            name = name.substring(1);
        }
        this.output = loader.loadSkill(name);
        this.skillDesc = loader.getSkillDescription(name);
    }

    @Override
    public CommandType getType() { return CommandType.SKILL; }

    @Override
    public String execute() {
        return this.output;
    }


    @Override
    public List<ContentBlock> toContentBlocks() {
        return List.of(
            new ContentBlock(
                "<command-message>" + message + "</command-message>\n"
              + "<command-name>" + name + "</command-name> \n"
            ),
            new ContentBlock(output)
        );
    }
}
