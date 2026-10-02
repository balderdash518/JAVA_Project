package com.compilequest.core;

import java.awt.Graphics2D;

/** One full-screen state of the game (the menu or a level). */
public interface Screen {
    default void enter(Game game) { }

    void update(Game game);

    void render(Graphics2D g);
}
