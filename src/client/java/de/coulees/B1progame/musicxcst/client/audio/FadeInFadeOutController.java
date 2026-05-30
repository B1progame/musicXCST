package de.coulees.B1progame.musicxcst.client.audio;

public final class FadeInFadeOutController {
    private FadeInFadeOutController() {
    }

    public static float linearGain(long elapsedMillis, int fadeInMillis) {
        if (fadeInMillis <= 0) {
            return 1.0F;
        }
        return Math.min(1.0F, Math.max(0.0F, elapsedMillis / (float) fadeInMillis));
    }
}
