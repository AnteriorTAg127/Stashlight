package strangequark.chestfinder;


import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ServerInfo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class Init {
    public static Path ROOT;

    public static void init() {
        if (FabricLoader.getInstance().getEnvironmentType() != EnvType.CLIENT) {
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();

        Path root = FabricLoader.getInstance()
                .getGameDir()
                .resolve("chestfinder_cache");

        ROOT = root;

        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static Path getFileName() {
        MinecraftClient client = MinecraftClient.getInstance();
        String fileName;

        ChestFinder.LOGGER.info("isInSingleplayer {}", client.isInSingleplayer());

        if (client.isInSingleplayer()) {
            if (client.getServer() == null) {
                return null;
            }

            String id = client.getServer()
                    .getSaveProperties()
                    .getLevelName()
                    .replace(' ', '_');
            fileName = id + ".json";
            ChestFinder.LOGGER.info("id {}", id);
        } else {
            ServerInfo info = client.getCurrentServerEntry();
            if (info == null) {
                return null;
            }
            String norm = info.address.replace(':', '_');
            fileName = "MP_" + norm + ".json";
        }

        ChestFinder.LOGGER.info("filename {}", fileName);

        return ROOT.resolve(fileName);
    }
}