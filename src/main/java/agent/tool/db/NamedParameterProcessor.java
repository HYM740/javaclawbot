package agent.tool.db;

import java.util.*;

/**
 * Processes named parameter SQL (e.g. "SELECT * FROM t WHERE id = :id")
 * into standard JDBC positional parameter SQL ("SELECT * FROM t WHERE id = ?").
 *
 * Supports colon-prefixed identifiers: :name, :user_id, :_type
 * Parameter map keys should include the colon, e.g. {":id": 123}
 */
public class NamedParameterProcessor {

    private final String originalSql;
    private final String parsedSql;
    private final List<String> paramNames;

    public NamedParameterProcessor(String sql) {
        this.originalSql = sql;
        this.paramNames = new ArrayList<>();
        this.parsedSql = parse(sql);
    }

    public String getParsedSql() {
        return parsedSql;
    }

    public List<String> getParamNames() {
        return paramNames;
    }

    /**
     * Convert named params map to positional values array matching the parsed ? order.
     * Keys in params should include the colon prefix (e.g. ":id").
     */
    public List<Object> toPositionalValues(Map<String, Object> namedParams) {
        List<Object> values = new ArrayList<>(paramNames.size());
        for (String name : paramNames) {
            Object val = namedParams.get(name);
            values.add(val);
        }
        return values;
    }

    private String parse(String sql) {
        if (sql == null || sql.isEmpty()) return sql;

        StringBuilder result = new StringBuilder(sql.length());
        int len = sql.length();

        for (int i = 0; i < len; i++) {
            char c = sql.charAt(i);

            if (c == ':' && i + 1 < len && isIdentifierStart(sql.charAt(i + 1))) {
                // Check it's not preceded by another colon (:: is cast in PostgreSQL)
                if (i > 0 && sql.charAt(i - 1) == ':') {
                    result.append(c);
                    continue;
                }

                int start = i + 1;
                int end = start;
                while (end < len && isIdentifierPart(sql.charAt(end))) {
                    end++;
                }

                String paramName = sql.substring(start, end);
                paramNames.add(":" + paramName);
                result.append('?');
                i = end - 1;
            } else {
                result.append(c);
            }
        }

        return result.toString();
    }

    private boolean isIdentifierStart(char c) {
        return Character.isJavaIdentifierStart(c);
    }

    private boolean isIdentifierPart(char c) {
        return Character.isJavaIdentifierPart(c);
    }
}
