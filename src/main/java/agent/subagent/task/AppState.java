package agent.subagent.task;

import agent.subagent.task.todo.TodoItem;
import agent.subagent.task.todo.TodoList;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.UnaryOperator;

/**
 * Application state holder for task management.
 *
 * 对应 Open-ClaudeCode: src/state/AppStateStore.ts - AppState
 *
 * 设计原则：不可变性
 * - 所有状态更新通过 setAppState() 进行
 * - 使用 UnaryOperator<AppState> 实现不可变更新
 */
public class AppState {

    private Map<String, TaskState> tasks = new ConcurrentHashMap<>();
    private Map<String, TodoList> todos = new ConcurrentHashMap<>();
    private Map<String, String> agentNameRegistry = new ConcurrentHashMap<>();

    /**
     * Reference to this instance for creating Setter.
     */
    private final AppState thisState = this;

    /**
     * Interface for setting AppState (corresponds to SetAppState in TypeScript).
     *
     * type SetAppState = (f: (prev: AppState) => AppState) => void
     */
    @FunctionalInterface
    public interface Setter {
        void accept(UnaryOperator<AppState> updater);
    }

    /**
     * Interface for getting AppState.
     *
     * type GetAppState = () => AppState
     */
    @FunctionalInterface
    public interface Getter {
        AppState get();
    }

    /**
     * Creates a Setter that updates this AppState instance.
     * This allows passing 'appState::set' as the setter.
     *
     * @return a Setter for this AppState
     */
    public Setter setter() {
        return updater -> {
            AppState updated = updater.apply(thisState);
            // Copy updated state back to this instance
            this.tasks.clear();
            this.tasks.putAll(updated.tasks);
            this.todos.clear();
            this.todos.putAll(updated.todos);
            this.agentNameRegistry.clear();
            this.agentNameRegistry.putAll(updated.agentNameRegistry);
        };
    }

    public Map<String, TaskState> getTasks() {
        return tasks;
    }

    public Map<String, TodoList> getTodos() {
        return todos;
    }

    public Map<String, String> getAgentNameRegistry() {
        return agentNameRegistry;
    }

    /**
     * Sets the tasks map (used for immutable updates).
     *
     * @param tasks the new tasks map
     */
    public void setTasks(Map<String, TaskState> tasks) {
        this.tasks.clear();
        if (tasks != null) {
            this.tasks.putAll(tasks);
        }
    }

    /**
     * Sets the todos map (used for immutable updates).
     *
     * @param todos the new todos map
     */
    public void setTodos(Map<String, TodoList> todos) {
        this.todos.clear();
        if (todos != null) {
            this.todos.putAll(todos);
        }
    }

    /**
     * Sets the agent name registry map (used for immutable updates).
     *
     * @param agentNameRegistry the new agent name registry map
     */
    public void setAgentNameRegistry(Map<String, String> agentNameRegistry) {
        this.agentNameRegistry.clear();
        if (agentNameRegistry != null) {
            this.agentNameRegistry.putAll(agentNameRegistry);
        }
    }

    /**
     * Gets todos for a specific key.
     *
     * @param key the todo key (agentId or sessionId)
     * @return the TodoList or null if not found
     */
    public TodoList getTodos(String key) {
        return todos.get(key);
    }

    /**
     * Sets todos for a specific key.
     *
     * @param key the todo key
     * @param todoList the TodoList to set
     */
    public void setTodos(String key, TodoList todoList) {
        todos.put(key, todoList);
    }

    /**
     * Registers a task state with the given ID.
     */
    public void registerTask(String taskId, TaskState taskState) {
        tasks.put(taskId, taskState);
    }

    /**
     * Gets a task state by ID.
     */
    public TaskState getTask(String taskId) {
        return tasks.get(taskId);
    }

    /**
     * Removes a task state by ID.
     */
    public TaskState removeTask(String taskId) {
        return tasks.remove(taskId);
    }

    /**
     * Registers an agent name for the given ID.
     */
    public void registerAgent(String agentId, String agentName) {
        agentNameRegistry.put(agentId, agentName);
    }

    /**
     * Gets an agent name by ID.
     */
    public String getAgentName(String agentId) {
        return agentNameRegistry.get(agentId);
    }

    /**
     * Removes an agent registration.
     */
    public String removeAgent(String agentId) {
        return agentNameRegistry.remove(agentId);
    }

    /**
     * Gets all active (non-terminal) tasks.
     *
     * @return list of active task states
     */
    public java.util.List<TaskState> getActiveTasks() {
        return tasks.values().stream()
                .filter(t -> !t.isTerminal())
                .collect(java.util.stream.Collectors.toList());
    }

    // =====================
    // 静态方法（对应 Open-ClaudeCode framework.ts）
    // =====================

    /**
     * Register a new task in AppState.
     * 对应 Open-ClaudeCode: src/utils/task/framework.ts - registerTask()
     *
     * @param task the task state to register
     * @param setAppState the state setter
     */
    public static void registerTask(TaskState task, Setter setAppState) {
        setAppState.accept(prev -> {
            Map<String, TaskState> newTasks = new HashMap<>(prev.tasks);
            newTasks.put(task.getId(), task);
            AppState newState = new AppState();
            newState.tasks = newTasks;
            newState.todos = new HashMap<>(prev.todos);
            newState.agentNameRegistry = new HashMap<>(prev.agentNameRegistry);
            return newState;
        });
    }

    /**
     * Update a task's state in AppState.
     * 对应 Open-ClaudeCode: src/utils/task/framework.ts - updateTaskState()
     *
     * Generic to allow type-safe updates for specific task types.
     *
     * @param taskId the task ID
     * @param setAppState the state setter
     * @param updater the update function
     */
    public static <T extends TaskState> void updateTaskState(
            String taskId,
            Setter setAppState,
            java.util.function.Function<T, T> updater
    ) {
        setAppState.accept(prev -> {
            TaskState task = prev.tasks.get(taskId);
            if (task == null) {
                return prev;
            }
            @SuppressWarnings("unchecked")
            T typedTask = (T) task;
            T updated = updater.apply(typedTask);
            if (updated == task) {
                // Updater returned the same reference (early-return no-op)
                return prev;
            }
            Map<String, TaskState> newTasks = new HashMap<>(prev.tasks);
            newTasks.put(taskId, updated);
            AppState newState = new AppState();
            newState.tasks = newTasks;
            newState.todos = new HashMap<>(prev.todos);
            newState.agentNameRegistry = new HashMap<>(prev.agentNameRegistry);
            return newState;
        });
    }

    /**
     * Evict a terminal task from AppState.
     * 对应 Open-ClaudeCode: src/utils/task/framework.ts - evictTerminalTask()
     *
     * @param taskId the task ID to evict
     * @param setAppState the state setter
     */
    public static void evictTerminalTask(String taskId, Setter setAppState) {
        setAppState.accept(prev -> {
            TaskState task = prev.tasks.get(taskId);
            if (task == null) {
                return prev;
            }
            if (!task.isTerminal()) {
                return prev;
            }
            Map<String, TaskState> newTasks = new HashMap<>(prev.tasks);
            newTasks.remove(taskId);
            AppState newState = new AppState();
            newState.tasks = newTasks;
            newState.todos = new HashMap<>(prev.todos);
            newState.agentNameRegistry = new HashMap<>(prev.agentNameRegistry);
            return newState;
        });
    }

    /**
     * Get all running tasks from AppState.
     * 对应 Open-ClaudeCode: src/utils/task/framework.ts - getRunningTasks()
     *
     * @param state the AppState
     * @return list of running tasks
     */
    public static java.util.List<TaskState> getRunningTasks(AppState state) {
        return state.tasks.values().stream()
                .filter(task -> task.getStatus() == TaskStatus.RUNNING)
                .collect(java.util.stream.Collectors.toList());
    }
}
