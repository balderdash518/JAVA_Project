package com.compilequest.entity;

/** Anything with a position and an axis-aligned hitbox. */
public abstract class Entity {
    public double x, y, w, h, vx, vy;
    public boolean dead;

    public double cx() { return x + w / 2; }
    public double cy() { return y + h / 2; }

    public boolean overlaps(Entity o) { return overlaps(o.x, o.y, o.w, o.h); }

    public boolean overlaps(double ox, double oy, double ow, double oh) {
        return x < ox + ow && x + w > ox && y < oy + oh && y + h > oy;
    }
}
