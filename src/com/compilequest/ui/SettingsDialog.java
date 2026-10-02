package com.compilequest.ui;

import com.compilequest.core.Draw;
import com.compilequest.core.Game;
import com.compilequest.core.Input;
import com.compilequest.core.Sound.Sfx;
import com.compilequest.core.Theme;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.event.KeyEvent;

/** The settings panel shown as a window over a level (opened from the pause menu). */
public final class SettingsDialog {
    private static final double W = 760, H = 380;

    private final Game game;
    private final Runnable onClose;
    private final SettingsPanel panel;
    private int t;

    public SettingsDialog(Game game, Runnable onClose, Runnable onReset) {
        this.game = game;
        this.onClose = onClose;
        this.panel = new SettingsPanel(game, onReset);
    }

    public void update(Game g) {
        t++;
        Input in = g.input;
        boolean clickable = panel.update(in, true);
        g.setCursor(clickable ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR);
        if (t > 2 && in.pressed(KeyEvent.VK_ESCAPE)) {
            game.sound.play(Sfx.CLICK);
            game.input.consume();
            g.setCursor(Cursor.DEFAULT_CURSOR);
            onClose.run();
        }
    }

    public void render(Graphics2D g) {
        double a = Draw.easeOut(Math.min(1, t / 8.0));
        Draw.rect(g, 0, 0, Theme.W, Theme.H, new Color(0, 0, 0, (int) (150 * a)));
        var old = Draw.pushAlpha(g, a);
        double x = (Theme.W - W) / 2, y = (Theme.H - H) / 2;
        Draw.shadow(g, x, y, W, H, 12, 24, 0.55);
        Draw.panel(g, x, y, W, H, 12, Theme.BG, Theme.BORDER_STRONG);
        Draw.panel(g, x, y, W, 44, 12, Theme.PANEL, null);
        Draw.rect(g, x, y + 32, W, 12, Theme.PANEL);
        Draw.rect(g, x, y + 44, W, 1, Theme.BORDER);
        Font tf = Theme.bold(15);
        Draw.fileIcon(g, x + 18, y + 14, Theme.SYN_NUMBER);
        Draw.text(g, "settings.json", x + 44, Draw.mid(tf, y, 44), tf, Theme.TEXT_BRIGHT);
        Font kf = Theme.mono(13);
        double base = Draw.mid(kf, y, 44);
        double cx = x + W - 20 - Draw.width(kf, "close");
        Draw.text(g, "close", cx, base, kf, Theme.TEXT_2);
        Draw.keycap(g, "Esc", cx - 10 - Math.max(Draw.width(kf, "Esc") + 12, 22), base, kf, Theme.TEXT);
        panel.render(g, x + 70, y + 66, true);
        g.setComposite(old);
    }
}
