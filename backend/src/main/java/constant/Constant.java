package constant;

public interface Constant {


    /** 项目指令文件最大读取行数 */
    int MAX_PROJECT_INSTRUCTION_LINES = 200;

    String importantPrompt = """
            \n**重要提示**：
            Windows + Bash 交叉环境命令提示： \s
            - 路径中的反斜杠 `\\` 会被 Bash 当作转义符（如 `\\c` 变成控制字符），应用单引号包裹路径或改用正斜杠。 \s
            - 多层双引号易导致配对混乱，触发 `unexpected EOF while looking for matching '"'`。 \s
            - 解决：参数尽量用单引号；若在 PowerShell 中，用反引号 `` ` `` 转义内部双引号。
            以后在 Windows bash 中传路径/嵌套参数时用单引号就能避开这个问题：
            # ❌ 会报错
            echo "path\\to\\file"
            # ✅ 正确
            echo 'path\\to\\file'
            """;
}
