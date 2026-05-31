package de.coulees.B1progame.musicxcst.init;

import de.coulees.B1progame.musicxcst.Musicxcst;
import de.coulees.B1progame.musicxcst.block.entity.CdWriterBlockEntity;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.entity.BlockEntityType;

public final class ModBlockEntities {
    public static final BlockEntityType<CdWriterBlockEntity> CD_WRITER = Registry.register(
            BuiltInRegistries.BLOCK_ENTITY_TYPE,
            Identifier.fromNamespaceAndPath(Musicxcst.MOD_ID, "cd_writer"),
            FabricBlockEntityTypeBuilder.create(CdWriterBlockEntity::new, ModBlocks.CD_WRITER).build()
    );

    private ModBlockEntities() {
    }

    public static void register() {
    }
}
