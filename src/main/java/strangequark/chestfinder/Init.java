package strangequark.chestfinder;


import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ServerInfo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class Init {
    private static Path ROOT;

    public static void init() {
        if (FabricLoader.getInstance().getEnvironmentType() != EnvType.CLIENT) {
            return;
        }

        ROOT = FabricLoader.getInstance()
                .getGameDir()
                .resolve("chestfinder_cache");

        try {
            Files.createDirectories(ROOT);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static Path getFileName() {
        MinecraftClient client = MinecraftClient.getInstance();
        String fileName;

        if (client.isInSingleplayer()) {
            if (client.getServer() == null) return null;

            String id = client.getServer()
                    .getSaveProperties()
                    .getLevelName()
                    .replaceAll("[^a-zA-Z0-9-_]", "_");

            fileName = id + ".dat";
        } else {
            ServerInfo info = client.getCurrentServerEntry();
            if (info == null) return null;

            String norm = info.address.replace(':', '_').replace('/', '_');
            fileName = "MP_" + norm + ".dat";
        }

        return ROOT.resolve(fileName);
    }
}