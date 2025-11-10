package strangequark.chestfinder.repository;

import com.google.gson.JsonObject;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import strangequark.chestfinder.mapper.ContainerMapper;
import strangequark.chestfinder.model.ContainerEntity;
import strangequark.chestfinder.model.ItemTile;
import strangequark.chestfinder.serializer.Serializer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ContainerRepository {
    private JsonObject cachedJson;
    private List<ContainerEntity> cachedItems;
    private final Serializer serializer;
    private final ContainerMapper mapper = new ContainerMapper();

    public ContainerRepository(Serializer serializer) {
        this.serializer = serializer;
    }

    public void save(String dimension, String containerName, BlockPos pos, List<ItemStack> containerStacks) {
        cachedJson = serializer.read();
        JsonObject dimObj = cachedJson.has(dimension) ? cachedJson.getAsJsonObject(dimension) : new JsonObject();
        JsonObject chestJson = mapper.toJson(containerName, containerStacks);

        dimObj.add(mapper.serializePos(pos), chestJson);

        cachedJson.add(dimension, dimObj);
        serializer.write(cachedJson);
        cachedItems = null;
    }


    public List<ContainerEntity> getAllItems() {
        if (cachedItems != null) return cachedItems;
        cachedJson = cachedJson != null ? cachedJson : serializer.read();
        cachedItems = mapper.toEntities(cachedJson);
        return cachedItems;
    }

    public List<ItemTile> getTiles() {
        List<ContainerEntity> items = getAllItems();

        Map<String, Map<String, Integer>> agg = new HashMap<>();
        Map<String, String> nameMap = new HashMap<>();
        Map<String, Long> timeMap = new HashMap<>();

        for (ContainerEntity e : items) {
            String key = e.dimension() + ":" + mapper.serializePos(e.pos());

            agg.computeIfAbsent(key, k -> new HashMap<>());
            agg.get(key).merge(e.itemId(), e.count(), Integer::sum);

            nameMap.putIfAbsent(key, e.containerName());
            timeMap.putIfAbsent(key, e.timestamp());
        }

        List<ItemTile> result = new ArrayList<>();

        for (var entry : agg.entrySet()) {
            String key = entry.getKey();
            String[] parts = key.split(":");
            BlockPos pos = mapper.toBlockPos(parts[1]);
            String containerName = nameMap.get(key);
            long ts = timeMap.get(key);

            for (var item : entry.getValue().entrySet()) {
                result.add(new ItemTile(parts[0], containerName, item.getKey(), pos, item.getValue(), ts));
            }
        }

        return result;
    }
}
