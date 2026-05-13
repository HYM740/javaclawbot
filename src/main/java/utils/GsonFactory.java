package utils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.time.LocalDateTime;

public final class GsonFactory {
    private static final com.fasterxml.jackson.databind.ObjectMapper M =
            new com.fasterxml.jackson.databind.ObjectMapper();
    private static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(LocalDateTime.class, new LocalDateTimeTypeAdapter())
            .disableHtmlEscaping()
            .create();

    private GsonFactory() {}

    public static Gson getGson() {
        return GSON;
    }

    // 将对象序列化为 JSON 字符串，失败时返回对象的字符串表示
    public static String toJson(Object o) {
        try {
            return M.writeValueAsString(o);
        } catch (Exception e) {
            return String.valueOf(o);
        }
    }
}