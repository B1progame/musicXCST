package de.coulees.B1progame.musicxcst.init;

import de.coulees.B1progame.musicxcst.Musicxcst;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

public final class ModSounds {
    public static final Identifier BLUEPRINT_CD_SILENCE_ID = Identifier.fromNamespaceAndPath(Musicxcst.MOD_ID, "blueprint_cd_silence");
    public static final SoundEvent BLUEPRINT_CD_SILENCE = SoundEvent.createVariableRangeEvent(BLUEPRINT_CD_SILENCE_ID);

    private ModSounds() {
    }

    public static void register() {
        Registry.register(BuiltInRegistries.SOUND_EVENT, BLUEPRINT_CD_SILENCE_ID, BLUEPRINT_CD_SILENCE);
    }
}
