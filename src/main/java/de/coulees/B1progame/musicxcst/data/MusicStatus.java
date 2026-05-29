package de.coulees.B1progame.musicxcst.data;

public final class MusicStatus {
    public static final String ACTIVE = "active";
    public static final String DELETED = "deleted";
    public static final String MISSING = "missing";
    public static final String INVALID = "invalid";

    private MusicStatus() {
    }

    public static boolean isInvalidLike(String status) {
        return DELETED.equals(status) || MISSING.equals(status) || INVALID.equals(status);
    }
}
