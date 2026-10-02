package com.compilequest.entity;

import com.compilequest.core.Draw;
import com.compilequest.core.Sound;
import com.compilequest.core.Theme;
import com.compilequest.level.LevelScreen;

import java.awt.Font;
import java.awt.Graphics2D;

/** A stationary "!Exception" that throws "throw" projectiles at the player. Cannot be destroyed. */
public final class ExceptionTurret extends Entity {
    private static final int PERIOD = 120, WARN = 30;
    private int timer = 70;
    private int face = -1;
    private int t;

    public ExceptionTurret(double cx, double bottom) {
        this.w = 116;
        this.h = 30;
        this.x = cx - w / 2;
        this.y = bottom - h;
    }

    public void update(LevelScreen L) {
        t++;
        Player p = L.player;
        face = p.cx() < cx() ? -1 : 1;
        boolean inRange = Math.abs(p.cx() - cx()) < 700 && Math.abs(p.cy() - cy()) < 230;
        if (!inRange) {
            timer = Math.max(timer, WARN + 20);
            return;
        }
        if (--timer <= 0) {
            timer = PERIOD;
            double sx = face > 0 ? x + w + 2 : x - 58;
            L.shots.add(new ThrowShot(sx, y + 6, face));
            L.sfx(Sound.Sfx.THROW, 0.8f);
        }
    }

    public boolean warning() { return timer <= WARN; }

    public void render(Graphics2D g, double camX, double camY) {
        double px = x - camX, py = y - camY;
        boolean flash = warning() && (timer / 4) % 2 == 0;
        Draw.glow(g, px + w / 2, py + h / 2, 70, Theme.EXCEPTION, flash ? 0.45 : 0.18);
        Draw.panel(g, px, py, w, h, 5, Draw.alpha(Theme.EXCEPTION, flash ? 0.4 : 0.22),
                flash ? Theme.ERR : Draw.alpha(Theme.EXCEPTION, 0.95));
        Font f = Theme.bold(14);
        Draw.center(g, "!Exception", px + w / 2, Draw.mid(f, py, h), f,
                flash ? java.awt.Color.WHITE : Draw.mix(Theme.EXCEPTION, java.awt.Color.WHITE, 0.4));
        // a little throwing hand on the facing side
        double hx = face > 0 ? px + w + 4 : px - 10;
        Font hf = Theme.bold(16);
        Draw.text(g, face > 0 ? ">" : "<", hx, Draw.mid(hf, py, h), hf, Draw.alpha(Theme.ERR, warning() ? 1 : 0.5));
        if (warning()) {
            Font big = Theme.bold(20);
            double bounce = Math.abs(Math.sin(t * 0.3)) * 4;
            Draw.outlined(g, "!", px + w / 2 - 5, py - 8 - bounce, big, Theme.ERR, Draw.RIM);
        }
    }
}
