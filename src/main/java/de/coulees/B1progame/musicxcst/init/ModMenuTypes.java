package de.coulees.B1progame.musicxcst.init;

import de.coulees.B1progame.musicxcst.Musicxcst;
import de.coulees.B1progame.musicxcst.menu.CdWriterMenu;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.MenuType;

public final class ModMenuTypes {
    public static final MenuType<CdWriterMenu> CD_WRITER = Registry.register(
            BuiltInRegistries.MENU,
            Identifier.fromNamespaceAndPath(Musicxcst.MOD_ID, "cd_writer"),
            new ExtendedMenuType<>(CdWriterMenu::new, BlockPos.STREAM_CODEC.cast())
    );

    private ModMenuTypes() {
    }

    public static void register() {
        Musicxcst.LOGGER.info("Registered musicXCST menus");
    }
}
