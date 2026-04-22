package agent.subagent.types;

import java.util.function.Function;

@FunctionalInterface
public interface SetAppState {
    void accept(Function<AppState, AppState> updater);
}
