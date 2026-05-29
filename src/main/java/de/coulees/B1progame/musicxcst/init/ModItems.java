package de.coulees.B1progame.musicxcst.init;

import de.coulees.B1progame.musicxcst.Musicxcst;
import de.coulees.B1progame.musicxcst.item.BlueprintCdItem;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;

public final class ModItems {
    public static final Item BLUEPRINT_CD = new BlueprintCdItem(new Item.Properties().stacksTo(16).rarity(Rarity.UNCOMMON));

    private ModItems() {
    }

    public static void register() {
        register("blueprint_cd", BLUEPRINT_CD);
    }

    private static void register(String path, Item item) {
        Registry.register(BuiltInRegistries.ITEM, Identifier.fromNamespaceAndPath(Musicxcst.MOD_ID, path), item);
    }
}
