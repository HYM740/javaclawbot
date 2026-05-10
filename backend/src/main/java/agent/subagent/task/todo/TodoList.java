package agent.subagent.task.todo;

import java.util.ArrayList;
import java.util.List;

/**
 * Todo list container.
 * 对应 Open-ClaudeCode: src/utils/todo/types.ts - TodoListSchema
 *
 * export type TodoList = TodoItem[]
 */
public class TodoList {

    private List<TodoItem> items;

    public TodoList() {
        this.items = new ArrayList<>();
    }

    public TodoList(List<TodoItem> items) {
        this.items = items != null ? items : new ArrayList<>();
    }

    public List<TodoItem> getItems() {
        return items;
    }

    public void setItems(List<TodoItem> items) {
        this.items = items;
    }

    public void add(TodoItem item) {
        this.items.add(item);
    }

    public void remove(int index) {
        if (index >= 0 && index < items.size()) {
            items.remove(index);
        }
    }

    public TodoItem get(int index) {
        return items.get(index);
    }

    public int size() {
        return items.size();
    }

    public boolean isEmpty() {
        return items.isEmpty();
    }

    /**
     * Check if all items are completed.
     */
    public boolean isAllCompleted() {
        if (items.isEmpty()) {
            return false;
        }
        for (TodoItem item : items) {
            if (!item.isCompleted()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Create a copy of this TodoList for immutable updates.
     */
    public TodoList copy() {
        List<TodoItem> copiedItems = new ArrayList<>();
        for (TodoItem item : items) {
            copiedItems.add(item.copy());
        }
        return new TodoList(copiedItems);
    }

    @Override
    public String toString() {
        return "TodoList{" +
                "items=" + items +
                '}';
    }
}
