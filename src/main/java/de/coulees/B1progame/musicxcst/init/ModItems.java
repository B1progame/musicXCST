package de.coulees.B1progame.musicxcst.init;

import de.coulees.B1progame.musicxcst.Musicxcst;
import de.coulees.B1progame.musicxcst.item.BlueprintCdItem;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.JukeboxSong;
import net.minecraft.world.item.Rarity;

public final class ModItems {
    public static final ResourceKey<Item> BLUEPRINT_CD_KEY = key("blueprint_cd");
    public static final ResourceKey<Item> CD_WRITER_KEY = key("cd_writer");
    public static final ResourceKey<JukeboxSong> BLUEPRINT_CD_SILENCE_SONG = ResourceKey.create(
            Registries.JUKEBOX_SONG,
            Identifier.fromNamespaceAndPath(Musicxcst.MOD_ID, "blueprint_cd_silence")
    );
    public static final Item BLUEPRINT_CD = register(BLUEPRINT_CD_KEY, new BlueprintCdItem(new Item.Properties()
            .setId(BLUEPRINT_CD_KEY)
            .stacksTo(1)
            .rarity(Rarity.UNCOMMON)
            .jukeboxPlayable(BLUEPRINT_CD_SILENCE_SONG)));
    public static final Item CD_WRITER = register(CD_WRITER_KEY, new BlockItem(ModBlocks.CD_WRITER, new Item.Properties()
            .setId(CD_WRITER_KEY)));

    private ModItems() {
    }

    public static void register() {
    }

    private static ResourceKey<Item> key(String path) {
        return ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(Musicxcst.MOD_ID, path));
    }

    private static Item register(ResourceKey<Item> key, Item item) {
        return Registry.register(BuiltInRegistries.ITEM, key.identifier(), item);
    }
}
