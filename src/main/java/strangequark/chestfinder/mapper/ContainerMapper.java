package strangequark.chestfinder.mapper;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ContainerComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;
import strangequark.chestfinder.model.ContainerEntity;

import java.util.ArrayList;
import java.util.List;

public class ContainerMapper {

    private static final String KEY_CONTAINER = "container";
    private static final String KEY_TIME = "time";
    private static final String KEY_CONTENT = "content";
    private static final String KEY_COUNT = "count";

    public JsonObject toJson(String containerName, List<ItemStack> stacks) {
        JsonObject chestObj = new JsonObject();
        chestObj.addProperty(KEY_CONTAINER, containerName);
        chestObj.addProperty(KEY_TIME, System.currentTimeMillis());

        JsonArray contentArray = new JsonArray();
        for (ItemStack stack : stacks) {
            if (!stack.isEmpty()) {
                contentArray.add(serializeStack(stack));
            }
        }
        chestObj.add(KEY_CONTENT, contentArray);

        return chestObj;
    }

    private JsonArray serializeStack(ItemStack stack) {
        JsonArray entry = new JsonArray();
        entry.add(Registries.ITEM.getId(stack.getItem()).getPath());

        ContainerComponent nestedContainer = stack.get(DataComponentTypes.CONTAINER);
        JsonObject nestedObj = null;

        if (nestedContainer != null) {
            JsonArray nestedContent = new JsonArray();
            nestedContainer.stream()
                    .filter(s -> !s.isEmpty())
                    .forEach(s -> nestedContent.add(serializeStack(s)));

            if (!nestedContent.isEmpty()) {
                nestedObj = new JsonObject();
                nestedObj.addProperty(KEY_COUNT, stack.getCount());
                nestedObj.add(KEY_CONTENT, nestedContent);
            }
        }

        if (nestedObj != null) {
            entry.add(nestedObj);
        } else {
            entry.add(stack.getCount());
        }

        return entry;
    }


    public List<ContainerEntity> toEntities(JsonObject data) {
        List<ContainerEntity> entities = new ArrayList<>();

        for (String dimension : data.keySet()) {
            JsonObject dimObj = data.getAsJsonObject(dimension);

            for (String posKey : dimObj.keySet()) {
                JsonObject chestObj = dimObj.getAsJsonObject(posKey);

                String containerName = chestObj.get(KEY_CONTAINER).getAsString();
                long timestamp = chestObj.get(KEY_TIME).getAsLong();
                BlockPos pos = toBlockPos(posKey);

                for (var el : chestObj.getAsJsonArray(KEY_CONTENT)) {
                    JsonArray entry = el.getAsJsonArray();
                    String itemId = entry.get(0).getAsString();

                    int count;
                    if (entry.get(1).isJsonPrimitive()) {
                        count = entry.get(1).getAsInt();
                    } else {
                        JsonObject nestedObj = entry.get(1).getAsJsonObject();
                        count = nestedObj.getAsJsonArray(KEY_CONTENT).size();
                    }

                    entities.add(new ContainerEntity(
                            itemId,
                            containerName,
                            dimension,
                            pos,
                            count,
                            timestamp
                    ));
                }
            }
        }

        return entities;
    }

    public String serializePos(BlockPos pos) {
        return pos.getX() + "_" + pos.getY() + "_" + pos.getZ();
    }

    public BlockPos toBlockPos(String posKey) {
        String[] parts = posKey.split("_");
        return new BlockPos(
                Integer.parseInt(parts[0]),
                Integer.parseInt(parts[1]),
                Integer.parseInt(parts[2])
        );
    }
}
