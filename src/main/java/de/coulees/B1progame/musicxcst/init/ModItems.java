package de.coulees.B1progame.musicxcst.init;

import de.coulees.B1progame.musicxcst.Musicxcst;
import de.coulees.B1progame.musicxcst.item.BlueprintCdItem;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;

public final class ModItems {
    public static final ResourceKey<Item> BLUEPRINT_CD_KEY = key("blueprint_cd");
    public static final Item BLUEPRINT_CD = register(BLUEPRINT_CD_KEY, new BlueprintCdItem(new Item.Properties()
            .setId(BLUEPRINT_CD_KEY)
            .stacksTo(16)
            .rarity(Rarity.UNCOMMON)));

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
