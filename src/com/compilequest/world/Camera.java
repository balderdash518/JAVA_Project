package com.compilequest.world;

import com.compilequest.core.Draw;
import com.compilequest.core.Theme;

import java.util.Random;

/** Follows the player with a dead zone and look-ahead; can be pointed elsewhere for a cutscene. */
public final class Camera {
    public double x, y;
    private double tx, ty;
    private double look;
    private double shake;
    private double shakeX, shakeY;
    private int mapW = Theme.W, mapH = Theme.H;
    private boolean focusing;
    private double fx, fy;
    private final Random rng = new Random();

    public void setBounds(int pxW, int pxH) {
        mapW = pxW;
        mapH = pxH;
    }

    public void snap(double cx, double cy) {
        tx = cx;
        ty = cy;
        x = clampX(cx - Theme.W / 2.0);
        y = clampY(cy - Theme.H / 2.0);
    }

    public void focus(double cx, double cy) {
        focusing = true;
        fx = cx;
        fy = cy;
    }

    public void clearFocus() {
        if (!focusing) return;
        focusing = false;
        tx = x + Theme.W / 2.0;
        ty = y + Theme.H / 2.0;
    }

    public void update(double pcx, double pcy, int facing) {
        if (focusing) {
            x += (clampX(fx - Theme.W / 2.0) - x) * 0.075;
            y += (clampY(fy - Theme.H / 2.0) - y) * 0.075;
        } else {
            look += (facing * 90 - look) * 0.035;
            double px = pcx + look, py = pcy - 30;
            if (px > tx + 90) tx = px - 90;
            else if (px < tx - 90) tx = px + 90;
            if (py > ty + 60) ty = py - 60;
            else if (py < ty - 70) ty = py + 70;
            x += (clampX(tx - Theme.W / 2.0) - x) * 0.12;
            y += (clampY(ty - Theme.H / 2.0) - y) * 0.12;
        }
        if (shake > 0.15) {
            shakeX = (rng.nextDouble() * 2 - 1) * shake;
            shakeY = (rng.nextDouble() * 2 - 1) * shake;
            shake *= 0.86;
        } else {
            shake = shakeX = shakeY = 0;
        }
    }

    public void addShake(double s) { shake = Math.max(shake, s); }

    /** Render position, rounded to half a logical pixel (one device pixel at 200% scaling). */
    public double rx() { return Math.round((x + shakeX) * 2) / 2.0; }
    public double ry() { return Math.round((y + shakeY) * 2) / 2.0; }

    private double clampX(double v) {
        if (mapW <= Theme.W) return (mapW - Theme.W) / 2.0;
        return Draw.clamp(v, 0, mapW - Theme.W);
    }

    private double clampY(double v) {
        if (mapH <= Theme.H) return (mapH - Theme.H) / 2.0;
        return Draw.clamp(v, 0, mapH - Theme.H);
    }
}
