package com.compilequest.entity;

/**
 * Movement feel, in pixels and frames at 60 FPS (1 tile = 32 px). Not final so the F1 debug
 * panel can adjust values live while play-testing.
 */
public final class Tuning {
    private Tuning() {}

    public static double MAX_SPEED = 4.0;
    public static double ACCEL = 0.8;
    public static double FRICTION = 0.7;
    public static double AIR_ACCEL = 0.5;
    public static double AIR_DRAG = 0.25;
    public static double GRAVITY = 0.55;
    public static double FALL_GRAVITY = 0.8;
    public static double MAX_FALL = 12;
    public static double JUMP_V = -12;
    /** The second jump in mid-air: about 3 tiles on top of the first jump's 3.9. */
    public static double DOUBLE_JUMP_V = -10.5;
    public static int AIR_JUMPS = 1;
    public static double JUMP_CUT = 0.45;
    public static double SPRING_V = -16;
    public static int COYOTE = 6;
    public static int BUFFER = 6;
    public static int INVINCIBLE = 60;
    public static int SHOOT_COOLDOWN = 24;

    /** Names and accessors for the debug panel. */
    public static final String[] NAMES = {"MAX_SPEED", "ACCEL", "FRICTION", "AIR_ACCEL", "GRAVITY", "FALL_GRAVITY", "JUMP_V", "JUMP_CUT", "SPRING_V", "DOUBLE_JUMP_V"};

    public static double get(int i) {
        return switch (i) {
            case 0 -> MAX_SPEED;
            case 1 -> ACCEL;
            case 2 -> FRICTION;
            case 3 -> AIR_ACCEL;
            case 4 -> GRAVITY;
            case 5 -> FALL_GRAVITY;
            case 6 -> JUMP_V;
            case 7 -> JUMP_CUT;
            case 8 -> SPRING_V;
            default -> DOUBLE_JUMP_V;
        };
    }

    public static void adjust(int i, int dir) {
        double step = switch (i) {
            case 0, 6, 8, 9 -> 0.25;
            case 7 -> 0.05;
            default -> 0.05;
        };
        double v = Math.round((get(i) + dir * step) * 100) / 100.0;
        switch (i) {
            case 0 -> MAX_SPEED = Math.max(0.5, v);
            case 1 -> ACCEL = Math.max(0.05, v);
            case 2 -> FRICTION = Math.max(0.05, v);
            case 3 -> AIR_ACCEL = Math.max(0.05, v);
            case 4 -> GRAVITY = Math.max(0.05, v);
            case 5 -> FALL_GRAVITY = Math.max(0.05, v);
            case 6 -> JUMP_V = Math.min(-1, v);
            case 7 -> JUMP_CUT = Math.max(0, Math.min(1, v));
            case 8 -> SPRING_V = Math.min(-1, v);
            default -> DOUBLE_JUMP_V = Math.min(-1, v);
        }
    }
}
