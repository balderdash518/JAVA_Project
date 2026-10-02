package com.compilequest.entity;

import com.compilequest.core.Draw;
import com.compilequest.core.Theme;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.util.ArrayList;
import java.util.List;

/** Chooses the 3x3 character frame for the player and draws it with squash, stretch and trails. */
public final class PlayerAnimator {
    private static final String[] IDLE = {" o ", "/|>", "/ \\"};
    private static final String[][] RUN = {
        {" o ", "/|>", "/ \\"},
        {" o ", "\\|>", " |\\"},
        {" o ", "/|>", "/ \\"},
        {" o ", "\\|>", "/| "},
    };
    private static final String[] JUMP = {" o ", "/|>", " ^ "};
    private static final String[] DOUBLE = {"\\o/", " |>", "/ \\"};
    private static final String[] FALL = {"\\o ", " |>", "/ \\"};
    private static final String[] SHOOT = {" o ", "/|)", "/ \\"};
    private static final String[] BRAKE = {" o/", "/| ", "/ \\"};
    private static final String[] TURN = {"|o|", "/|\\", "/ \\"};
    private static final String[] HURT = {" x ", "\\|/", "/ \\"};

    private static final Color OUTLINE = new Color(8, 9, 11, 220);

    private double runPhase;
    private int frame;
    private int idle;
    private int ghostTimer;
    public boolean footstep;

    private static final class Ghost {
        final double x, y;
        final String[] rows;
        int life = 14;

        Ghost(double x, double y, String[] rows) {
            this.x = x;
            this.y = y;
            this.rows = rows;
        }
    }

    private final List<Ghost> ghosts = new ArrayList<>();

    public void update(Player p) {
        footstep = false;
        if (p.state == Player.State.RUN) {
            double speed = Math.abs(p.vx) / Tuning.MAX_SPEED;
            double interval = 10 - 5 * Math.min(1, speed);
            int before = (int) runPhase;
            runPhase += 1 / interval;
            int after = (int) runPhase;
            frame = after % 4;
            if (after != before && (frame == 0 || frame == 2)) footstep = true;
        } else {
            runPhase = 0;
            frame = 0;
        }
        idle = p.state == Player.State.IDLE ? idle + 1 : 0;
        for (int i = ghosts.size() - 1; i >= 0; i--) {
            if (--ghosts.get(i).life <= 0) ghosts.remove(i);
        }
        if (p.onGround && Math.abs(p.vx) >= Tuning.MAX_SPEED * 0.9) {
            if (++ghostTimer >= 4) {
                ghostTimer = 0;
                ghosts.add(new Ghost(p.x + Player.W / 2, p.y + Player.H, rows(p)));
            }
        } else {
            ghostTimer = 0;
        }
    }

    public String[] rows(Player p) {
        String[] base = switch (p.state) {
            case RUN -> RUN[frame];
            case JUMP -> p.doubleJumpAnim > 0 ? DOUBLE : JUMP;
            case FALL -> FALL;
            case BRAKE -> BRAKE;
            case TURN -> TURN;
            case HURT, DEAD -> HURT;
            default -> IDLE;
        };
        if (p.shootAnim > 0 && p.state != Player.State.HURT && p.state != Player.State.DEAD) {
            base = new String[] {base[0], SHOOT[1], base[2]};
        }
        return p.facing < 0 ? mirror(base) : base;
    }

    private static String[] mirror(String[] rows) {
        String[] out = new String[rows.length];
        for (int r = 0; r < rows.length; r++) {
            StringBuilder sb = new StringBuilder(rows[r]).reverse();
            for (int i = 0; i < sb.length(); i++) {
                char c = sb.charAt(i);
                sb.setCharAt(i, switch (c) {
                    case '/' -> '\\';
                    case '\\' -> '/';
                    case '>' -> '<';
                    case '<' -> '>';
                    case '(' -> ')';
                    case ')' -> '(';
                    default -> c;
                });
            }
            out[r] = sb.toString();
        }
        return out;
    }

    public void render(Graphics2D g, Player p, double camX, double camY) {
        double cx = p.x + Player.W / 2 - camX;
        double feet = p.y + Player.H - camY;

        if (p.onGround) {
            g.setColor(new Color(0, 0, 0, 95));
            g.fill(new Ellipse2D.Double(cx - 15, feet - 3, 30, 6));
        }
        for (Ghost gh : ghosts) {
            drawRows(g, gh.rows, gh.x - camX, gh.y - camY, gh.life / 14.0 * 0.28, false, false);
        }

        double sx = 1, sy = 1;
        if (p.landAnim > 0) {
            double k = p.landAnim / 6.0;
            sx = 1 + 0.16 * k;
            sy = 1 - 0.2 * k;
        } else if (p.jumpAnim > 0) {
            double k = p.jumpAnim / 6.0;
            sx = 1 - 0.1 * k;
            sy = 1 + 0.16 * k;
        }
        double bob = 0;
        if (p.state == Player.State.IDLE && (idle / 30) % 2 == 1) bob = 1;
        if (p.state == Player.State.RUN && (frame == 1 || frame == 3)) bob = -1.5;

        AffineTransform saved = g.getTransform();
        g.translate(cx, feet);
        g.scale(sx, sy);
        boolean blink = p.invincible > 0 && (p.invincible / 4) % 2 == 0;
        drawRows(g, rows(p), 0, bob, blink ? 0.35 : 1, true, p.state == Player.State.HURT || p.state == Player.State.DEAD);
        g.setTransform(saved);
    }

    private static void drawRows(Graphics2D g, String[] rows, double cx, double feet, double alpha, boolean outline, boolean hurt) {
        Font body = Theme.bold(17);
        Font head = Theme.bold(19);
        double lineH = 14.5;
        for (int r = 0; r < 3; r++) {
            Font f = r == 0 ? head : body;
            double adv = Draw.adv(f);
            String s = rows[r];
            double start = cx - s.length() * adv / 2;
            double base = feet - (2 - r) * lineH - 2;
            g.setFont(f);
            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                if (c == ' ') continue;
                float x = (float) (start + i * adv), y = (float) base;
                String str = Draw.ch(c);
                if (outline) {
                    g.setColor(Draw.alpha(OUTLINE, alpha));
                    g.drawString(str, x - 1.2f, y);
                    g.drawString(str, x + 1.2f, y);
                    g.drawString(str, x, y - 1.2f);
                    g.drawString(str, x, y + 1.4f);
                }
                Color col;
                if (!outline) col = Theme.TEXT_2;
                else if (hurt) col = Theme.ERR;
                else if (r == 1 && (c == '>' || c == '<' || c == ')' || c == '(')) col = Theme.ARROW;
                else col = Theme.PLAYER;
                g.setColor(Draw.alpha(col, alpha));
                g.drawString(str, x, y);
            }
        }
    }
}
