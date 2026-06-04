package de.coulees.B1progame.musicxcst.mixin.client;

import de.coulees.B1progame.musicxcst.client.audio.CustomAudioEngine;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SoundEngine.class)
public abstract class SoundEngineVolumeMixin {
    @Inject(method = "calculateVolume(Lnet/minecraft/client/resources/sounds/SoundInstance;)F", at = @At("RETURN"), cancellable = true)
    private void musicxcst$scaleJukeboxRecordVolume(SoundInstance sound, CallbackInfoReturnable<Float> cir) {
        if (sound.getSource() != SoundSource.RECORDS) {
            return;
        }
        BlockPos pos = BlockPos.containing(sound.getX(), sound.getY(), sound.getZ());
        cir.setReturnValue(cir.getReturnValueF() * CustomAudioEngine.jukeboxVolume(pos));
    }
}
