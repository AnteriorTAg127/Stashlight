package strangequark.containerlookup;


import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.WorldSavePath;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class Init {
    private static Path ROOT;


    public static void init() {
        if (FabricLoader.getInstance().getEnvironmentType() != EnvType.CLIENT) {
            throw new IllegalStateException("ChestFinder initialized outside client");
        }

        ROOT = FabricLoader.getInstance()
                .getGameDir()
                .resolve("chestfinder_cache");

        try {
            Files.createDirectories(ROOT);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create ChestFinder cache dir", e);
        }
    }

    public static Path getFileName() {
        MinecraftClient client = MinecraftClient.getInstance();
        String fileName;

        if (client.isInSingleplayer()) {
            var server = client.getServer();
            if (server == null) {
                throw new IllegalStateException("Singleplayer server missing");
            }
            Path worldFolder = server.getSavePath(WorldSavePath.ROOT).normalize();

            fileName = worldFolder.getFileName().toString()
                    .replace(" ", "_")
                    .replace("(", "_")
                    .replace(")", "_");
        } else {
            var info = client.getCurrentServerEntry();
            if (info == null) {
                throw new IllegalStateException("Multiplayer server info missing");
            }
            fileName = "MP_" + info.address.replace(':', '_').replace('/', '_');
        }

        return ROOT.resolve(fileName + ".dat");
    }
}