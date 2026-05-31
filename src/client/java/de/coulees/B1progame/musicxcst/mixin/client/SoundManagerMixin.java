package de.coulees.B1progame.musicxcst.mixin.client;

import de.coulees.B1progame.musicxcst.client.audio.CustomAudioEngine;
import net.minecraft.client.Minecraft;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.sounds.SoundSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SoundManager.class)
public abstract class SoundManagerMixin {
    @Inject(method = "pauseAllExcept", at = @At("TAIL"))
    private void musicxcst$resumeCustomAudioAfterPause(SoundSource[] sources, CallbackInfo ci) {
        CustomAudioEngine.resumeAll();
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void musicxcst$tickCustomAudioWhilePaused(boolean paused, CallbackInfo ci) {
        if (paused) {
            CustomAudioEngine.tick(Minecraft.getInstance());
        }
    }
}
