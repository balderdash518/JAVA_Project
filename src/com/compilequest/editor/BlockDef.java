package com.compilequest.editor;

/** A code block the player can collect: a whole LINE of code, or a TOKEN that fills a gap in a line. */
public record BlockDef(String id, Kind kind, String text) {
    public enum Kind { LINE, TOKEN }
}
