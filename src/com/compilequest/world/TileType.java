package com.compilequest.world;

/** Every kind of map cell. Solid cells block movement; the word is spelled across runs of cells. */
public enum TileType {
    EMPTY(false, null),
    SOLID(true, null),
    COMMENT(false, null),
    SPRING(true, null),
    SPIKE(false, "null"),
    SPIKE_DOWN(false, "null"),
    BREAK(true, "break"),
    TRANSIENT(true, "transient"),
    VOLATILE(true, "volatile"),
    FINAL(true, "final"),
    WATER(false, null),
    BRIDGE(true, null),
    LIFT(true, null),
    PILLAR(true, null),
    DOOR(true, "door"),
    HINT(false, null),
    ANCHOR(false, null);

    public final boolean solid;
    public final String word;

    TileType(boolean solid, String word) {
        this.solid = solid;
        this.word = word;
    }
}
