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

    public static String toJson(Object o) {
        try {
            return M.writeValueAsString(o);
        } catch (Exception e) {
            return String.valueOf(o);
        }
    }
}