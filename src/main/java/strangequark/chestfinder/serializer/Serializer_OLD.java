package strangequark.chestfinder.serializer;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import strangequark.chestfinder.ChestFinder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public record Serializer_OLD(Path file) {
    private static final Gson GSON = new GsonBuilder().create();

    public JsonObject read() {
        if (!Files.exists(file)) return new JsonObject();
        try {
            JsonObject data = GSON.fromJson(Files.readString(file), JsonObject.class);
            return data != null ? data : new JsonObject();
        } catch (IOException e) {
            ChestFinder.LOGGER.error("Failed to read JSON: {}", e.getMessage(), e);
            return new JsonObject();
        }
    }

    public void write(JsonObject data) {
        try {
            Files.writeString(file, GSON.toJson(data));
        } catch (IOException e) {
            ChestFinder.LOGGER.error("Failed to write JSON: {}", e.getMessage(), e);
        }
    }
}
