package com.compilequest.ui;

import com.compilequest.core.Draw;
import com.compilequest.core.Game;
import com.compilequest.core.Input;
import com.compilequest.core.Sound.Sfx;
import com.compilequest.core.Theme;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;

/** Memory hit zero: a stack trace types out, then any key "catches" it and respawns (DESIGN.md 3.6). */
public final class DeathOverlay {
    private final String[] lines;
    private final int total;
    private int t;
    private int catchT = -1;

    public DeathOverlay(String cause, String levelClass) {
        lines = new String[] {
            "Exception in thread \"main\" java.lang.OutOfMemoryError: Memory 0/5",
            "    at Player.hurt(Player.java:87)",
            "    at " + frame(cause, levelClass),
            "    at GameLoop.run(GameLoop.java:15)",
        };
        int n = 0;
        for (String s : lines) n += s.length();
        total = n;
    }

    private static String frame(String cause, String levelClass) {
        return switch (cause) {
            case "Bug.bite" -> "Bug.bite(Bug.java:21)";
            case "Exception.throw" -> "Exception.throw(" + levelClass + ".java:64)";
            case "Exception.touch" -> "Exception.touch(" + levelClass + ".java:61)";
            case "Water.drown" -> "Water.drown(" + levelClass + ".java:33)";
            case "World.fallOut" -> "World.fallOut(World.java:12)";
            case "Javac.compile" -> "Javac.compile(" + levelClass + ".java:1)";
            default -> cause + "(" + levelClass + ".java:42)";
        };
    }

    /** Returns true when the respawn should happen. */
    public boolean update(Game game) {
        t++;
        Input in = game.input;
        int typed = typedChars();
        if (catchT < 0) {
            if (typed >= total && t > 50 && in.anyPressed) {
                catchT = 0;
                game.sound.play(Sfx.CLICK);
            } else if (typed < total && t % 3 == 0) {
                game.sound.vary(Sfx.TYPE, 0.35f);
            }
            return false;
        }
        return ++catchT >= 40;
    }

    private int typedChars() { return Math.max(0, (t - 24) * 3); }

    public void render(Graphics2D g) {
        double dim = Math.min(1, t / 30.0);
        if (catchT >= 0) {
            double fade = 1 - catchT / 40.0;
            var old = Draw.pushAlpha(g, Math.min(1, fade * 1.6));
            Font f = Theme.bold(22);
            String s = "catch (OutOfMemoryError e) { respawn(); }";
            Draw.glow(g, Theme.W / 2.0, Theme.H / 2.0, 340, Theme.CHECKPOINT, 0.2);
            Draw.center(g, s, Theme.W / 2.0, Theme.H / 2.0 + 8, f, Theme.CHECKPOINT);
            g.setComposite(old);
            return;
        }
        double x = 230, y = 330;
        int left = typedChars();
        Draw.glow(g, Theme.W / 2.0, y + 30, 520, Theme.ERR, 0.08 * dim);
        for (int i = 0; i < lines.length && left > 0; i++) {
            String s = lines[i];
            String shown = s.substring(0, Math.min(s.length(), left));
            left -= s.length();
            Font f = i == 0 ? Theme.bold(19) : Theme.mono(18);
            Color c = i == 0 ? Theme.ERR : Draw.mix(Theme.ERR, Theme.BG, 0.25);
            Draw.text(g, shown, x, y + i * 32, f, c);
        }
        if (typedChars() >= total && t > 50) {
            Font pf = Theme.mono(17);
            double py = y + lines.length * 32 + 52;
            double blink = 0.55 + 0.45 * Math.sin(t * 0.12);
            Draw.text(g, "> Press any key to catch and respawn at try {", x, py, pf, Draw.alpha(Theme.TEXT, blink));
        }
    }
}
