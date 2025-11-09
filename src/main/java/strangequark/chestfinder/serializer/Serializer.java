package strangequark.chestfinder.serializer;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ContainerComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import strangequark.chestfinder.ChestFinder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class Serializer {
    private static final Gson GSON = new GsonBuilder().create();
    private final Path file;

    public Serializer(Path file) {
        this.file = file;
    }

    public void saveContainer(String dimension, String containerName, int[] posArray, List<ItemStack> containerStacks) {
        if (this.file == null) {
            ChestFinder.LOGGER.error("FilePath null");
            return;
        }

        String posKey = posArray[0] + "_" + posArray[1] + "_" + posArray[2];
        JsonObject chestObj = new JsonObject();
        chestObj.addProperty("container", containerName);
        chestObj.addProperty("time", System.currentTimeMillis());

        JsonArray contentArray = new JsonArray();
        for (ItemStack stack : containerStacks) {
            if (!stack.isEmpty()) {
                contentArray.add(serializeStack(stack));
            }
        }
        chestObj.add("content", contentArray);

        writeDataToFile(dimension, posKey, chestObj);
    }

    private JsonArray serializeStack(ItemStack stack) {
        JsonArray entry = new JsonArray();
        entry.add(String.valueOf(Registries.ITEM.getId(stack.getItem()).getPath()));

        ContainerComponent container = stack.get(DataComponentTypes.CONTAINER);
        
        if (container == null) {
            entry.add(stack.getCount());
        } else {
            JsonObject containerObj = new JsonObject();
            JsonArray beItems = new JsonArray();
            container.stream()
                    .filter(s -> !s.isEmpty())
                    .forEach(s -> {
                        JsonArray nestedEntry = new JsonArray();
                        nestedEntry.add(String.valueOf(Registries.ITEM.getId(s.getItem()).getPath()));
                        nestedEntry.add(s.getCount());
                        beItems.add(nestedEntry);
                    });
            containerObj.add("be_items", beItems);
            entry.add(containerObj);
        }
        return entry;
    }

    private void writeDataToFile(String dimension, String posKey, JsonObject chestObj) {
        try {
            JsonObject data;
            if (Files.exists(this.file)) {
                data = GSON.fromJson(Files.readString(this.file), JsonObject.class);
                if (data == null) data = new JsonObject();
            } else {
                data = new JsonObject();
            }

            JsonObject dimensionObj = data.has(dimension) ? data.getAsJsonObject(dimension) : new JsonObject();
            dimensionObj.add(posKey, chestObj);
            data.add(dimension, dimensionObj);

            Files.writeString(this.file, GSON.toJson(data));
        } catch (IOException e) {
            ChestFinder.LOGGER.error("Failed to write container JSON: {}", e.getMessage(), e);
        }
    }
}