package strangequark.chestfinder.render;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.NotNull;
import strangequark.chestfinder.model.HighlightPos;
import strangequark.chestfinder.model.IndexedItem;
import strangequark.chestfinder.util.Util;

import java.util.ArrayList;
import java.util.List;

public final class HighlightManager {
    private static final List<HighlightPos> highlights = new ArrayList<>();

    private HighlightManager() {
    } // prevent instantiation

    public static boolean tryHighlight(IndexedItem item) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) return false;

        String currentDim = Util.getDimensionName(client.world);
        if (!currentDim.equals(item.dimension())) {
            notifyWrongDimension(client.player);
            return false;
        }

        highlights.add(new HighlightPos(item.pos(), System.currentTimeMillis()));
        return true;
    }

    public static void removeExpired() {
        long now = System.currentTimeMillis();
        highlights.removeIf(h -> HighlightEffect.isExpired(now - h.startTimeMillis()));
    }

    public static List<HighlightPos> getActiveHighlights() {
        long now = System.currentTimeMillis();
        List<HighlightPos> active = new ArrayList<>();
        for (HighlightPos h : highlights) {
            if (!HighlightEffect.isExpired(now - h.startTimeMillis())) {
                active.add(h);
            }
        }
        return active;
    }

    private static void lookAt(PlayerEntity player, BlockPos target) {
        double d = target.getX() + 0.5 - player.getX();
        double e = target.getY() + 0.5 - player.getEyeY();
        double f = target.getZ() + 0.5 - player.getZ();
        double g = Math.sqrt(d * d + f * f);

        float yaw = (float) (Math.atan2(f, d) * 180.0 / Math.PI) - 90.0f;
        float pitch = (float) (-(Math.atan2(e, g) * 180.0 / Math.PI));

        player.setYaw(yaw);
        player.setPitch(pitch);
    }

    private static void notifyWrongDimension(@NotNull ClientPlayerEntity player) {
        player.sendMessage(Text.literal("Container is in a different dimension.").formatted(Formatting.RED), false);
    }
}
