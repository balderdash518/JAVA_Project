package com.compilequest.level;

/** One beat of a program's effect on the map: wait {@code delay} frames, then run {@code action}. */
public record Step(int delay, Runnable action) { }
