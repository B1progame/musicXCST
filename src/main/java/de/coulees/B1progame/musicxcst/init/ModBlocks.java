package de.coulees.B1progame.musicxcst.init;

import de.coulees.B1progame.musicxcst.Musicxcst;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;

public final class ModBlocks {
    public static final ResourceKey<Block> CD_WRITER_KEY = key("cd_writer");
    public static final Block CD_WRITER = register(CD_WRITER_KEY, new Block(BlockBehaviour.Properties.of()
            .setId(CD_WRITER_KEY)
            .strength(3.5F)
            .requiresCorrectToolForDrops()));

    private ModBlocks() {
    }

    public static void register() {
    }

    private static ResourceKey<Block> key(String path) {
        return ResourceKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath(Musicxcst.MOD_ID, path));
    }

    private static Block register(ResourceKey<Block> key, Block block) {
        return Registry.register(BuiltInRegistries.BLOCK, key.identifier(), block);
    }
}
