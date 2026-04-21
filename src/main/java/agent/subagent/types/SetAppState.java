package agent.subagent.types;

import java.util.function.Function;

/**
 * 状态更新函数式接口
 *
 * 用于在 TaskFramework 中更新任务状态
 */
@FunctionalInterface
public interface SetAppState {
    /**
     * 应用状态更新
     * @param updater 状态更新函数
     */
    void accept(Function<AppState, AppState> updater);
}
