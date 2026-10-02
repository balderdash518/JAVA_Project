package com.compilequest.level;

import com.compilequest.core.Draw;
import com.compilequest.core.Input;
import com.compilequest.core.Sound.Sfx;
import com.compilequest.core.Theme;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The cutscene that plays when a program runs: compile, pan to the target, print the output
 * while the map changes, then show the result (DESIGN.md 3.4).
 */
final class RunSequence {
    enum Phase { COMPILE, PAN, EXEC, RESULT, RETURN }

    private final LevelScreen L;
    private final LevelConfig.Outcome out;
    private final List<Step> steps = new ArrayList<>();
    private final String output;
    private final double fx, fy;
    private Phase phase = Phase.COMPILE;
    private int t, total;
    private int stepIdx, stepWait;
    private int typed, typeWait;
    private int hold;

    RunSequence(LevelScreen L, LevelConfig.Outcome out) {
        this.L = L;
        this.out = out;
        L.revertRun();
        for (Map<String, Object> e : out.effects) steps.addAll(Effects.build(L, e));
        this.output = out.output == null ? "" : out.output;
        double[] f = Effects.focus(L, out);
        fx = f[0];
        fy = f[1];
        stepWait = steps.isEmpty() ? 0 : steps.get(0).delay();
        L.sfx(Sfx.WHOOSH, 0.5f);
    }

    Phase phase() { return phase; }

    void update(Input in) {
        t++;
        total++;
        boolean skip = in.pressedAny(KeyEvent.VK_SPACE, KeyEvent.VK_ENTER);
        switch (phase) {
            case COMPILE -> {
                if (t % 9 == 0) L.sfxVary(Sfx.TYPE, 0.3f);
                if (t >= 44 || skip) go(Phase.PAN);
            }
            case PAN -> {
                L.cam.focus(fx, fy);
                if (skip) {
                    finishAll();
                    go(Phase.RESULT);
                } else if (t >= 40) {
                    go(Phase.EXEC);
                }
            }
            case EXEC -> {
                L.cam.focus(fx, fy);
                boolean canType = !out.outputAfter || stepIdx >= steps.size();
                if (canType && typed < output.length() && --typeWait <= 0) {
                    char c = output.charAt(typed++);
                    typeWait = 5;
                    if (c != '\n' && c != ' ') L.sfxVary(Sfx.TYPE, 0.45f);
                }
                if (stepIdx < steps.size() && --stepWait <= 0) {
                    steps.get(stepIdx++).action().run();
                    if (stepIdx < steps.size()) stepWait = steps.get(stepIdx).delay();
                }
                if (skip) {
                    finishAll();
                    go(Phase.RESULT);
                } else if (stepIdx >= steps.size() && typed >= output.length() && ++hold > 24) {
                    go(Phase.RESULT);
                }
            }
            case RESULT -> {
                L.cam.focus(fx, fy);
                if (t == 1) apply();
                if (t >= (out.success ? 110 : 120) || (skip && t > 15)) go(Phase.RETURN);
            }
            case RETURN -> {
                L.cam.clearFocus();
                if (t >= 24) L.finishRun();
            }
        }
    }

    private void go(Phase p) {
        phase = p;
        t = 0;
    }

    private void finishAll() {
        while (stepIdx < steps.size()) steps.get(stepIdx++).action().run();
        typed = output.length();
    }

    private void apply() {
        if (out.success) {
            L.solved = true;
            L.exit.activate();
            L.okFlash = 44;
            L.sfx(Sfx.BUILD_OK);
            L.cam.addShake(3);
            L.particles.burst(L.exit.cx(), L.exit.cy(), "{}();=+", Theme.EXIT, 26, 5, 15);
            L.addConsole(out.message.isEmpty() ? "BUILD SUCCESSFUL. The exit } is open." : out.message, Theme.OK, false);
        } else {
            L.fails++;
            L.sfx(Sfx.BUILD_FAIL);
            L.hurtFlash = 40;
            L.cam.addShake(8);
            L.player.hp = Math.max(0, L.player.hp - 1);
            L.hud.loseMemory(L.player.hp);
            L.deathCause = "Javac.compile";
            L.addConsole(out.message.isEmpty() ? "error: the program does not do what the task needs" : out.message, Theme.ERR, true);
        }
    }

    // ---------------------------------------------------------------- rendering

    private record Line(String text, Color color, boolean cursor) { }

    void render(Graphics2D g) {
        double w = 680, h = 158;
        double x = (Theme.W - w) / 2;
        double slide = phase == Phase.RETURN ? Draw.easeInOut(t / 24.0) : 1 - Draw.easeOut(Math.min(1, total / 14.0));
        double y = Theme.H - h - 34 + slide * (h + 60);
        Draw.shadow(g, x, y, w, h, 10, 18, 0.5);
        Draw.panel(g, x, y, w, h, 10, Draw.alpha(Theme.PANEL_DARK, 0.97), Theme.BORDER_STRONG);
        Draw.panel(g, x, y, w, 34, 10, Theme.PANEL, null);
        Draw.rect(g, x, y + 24, w, 10, Theme.PANEL);
        Draw.rect(g, x, y + 34, w, 1, Theme.BORDER);
        Font hf = Theme.bold(13);
        Draw.play(g, x + 16, y + 11, 12, Theme.OK);
        Draw.text(g, "Run: " + L.cfg.className(), x + 36, Draw.mid(hf, y, 34), hf, Theme.TEXT_BRIGHT);
        if (phase != Phase.RESULT && phase != Phase.RETURN) {
            Font kf = Theme.mono(12);
            double kw = Draw.width(kf, "Skip");
            Draw.text(g, "Skip", x + w - 16 - kw, Draw.mid(kf, y, 34), kf, Theme.TEXT_2);
            Draw.keycap(g, "Space", x + w - 26 - kw - Draw.width(kf, "Space") - 12, Draw.mid(kf, y, 34), kf, Theme.TEXT);
        }

        List<Line> lines = new ArrayList<>();
        int dots = phase == Phase.COMPILE ? (t / 8) % 4 : 3;
        String compile = "> javac " + L.cfg.file + " " + ".".repeat(dots);
        lines.add(new Line(compile + (phase == Phase.COMPILE ? "" : " done"), Theme.TEXT_2, false));
        if (phase != Phase.COMPILE) lines.add(new Line("> java " + L.cfg.className(), Theme.TEXT_2, false));
        if (phase == Phase.EXEC || phase == Phase.RESULT || phase == Phase.RETURN) {
            String shown = output.substring(0, Math.min(typed, output.length()));
            String[] parts = shown.split("\n", -1);
            for (int i = 0; i < parts.length; i++) {
                boolean last = i == parts.length - 1;
                if (parts[i].isEmpty() && !last) continue;
                if (parts[i].isEmpty() && last && phase != Phase.EXEC) continue;
                lines.add(new Line(parts[i], Theme.TEXT_BRIGHT, last && phase == Phase.EXEC));
            }
        }
        if (phase == Phase.RESULT || phase == Phase.RETURN) {
            if (!out.success && !out.error.isEmpty()) lines.add(new Line(out.error, Theme.ERR, false));
            lines.add(new Line("Process finished with exit code " + (out.success ? 0 : 1), out.success ? Theme.OK : Theme.TEXT_2, false));
        }
        Font f = Theme.mono(14);
        int max = 5;
        int from = Math.max(0, lines.size() - max);
        double ly = y + 58;
        for (int i = from; i < lines.size(); i++) {
            Line ln = lines.get(i);
            Draw.text(g, ln.text, x + 18, ly, f, ln.color);
            if (ln.cursor && (total / 15) % 2 == 0) {
                Draw.rect(g, x + 18 + Draw.width(f, ln.text) + 2, ly - Draw.cap(f) - 1, 8, Draw.cap(f) + 3, Theme.TEXT);
            }
            ly += 21;
        }

        if (phase == Phase.RESULT) renderBanner(g);
    }

    private void renderBanner(Graphics2D g) {
        double life = out.success ? 110 : 120;
        double in = Draw.easeOutBack(Math.min(1, t / 16.0));
        double fade = Math.min(1, (life - t) / 18.0);
        double a = Math.max(0, Math.min(1, t / 8.0)) * fade;
        Color c = out.success ? Theme.OK : Theme.ERR;
        String title = out.success ? "BUILD SUCCESSFUL" : "BUILD FAILED";
        String sub = out.success ? "The exit } is open" : "Memory -1";
        Font tf = Theme.bold(out.success ? 46 : 40);
        Font sf = Theme.mono(17);
        double cy = 250;
        double bandH = 132;
        Draw.rect(g, 0, cy - bandH / 2, Theme.W, bandH, Draw.alpha(Theme.BG_DEEP, 0.72 * a));
        Draw.rect(g, 0, cy - bandH / 2, Theme.W, 1.5, Draw.alpha(c, 0.6 * a));
        Draw.rect(g, 0, cy + bandH / 2 - 1.5, Theme.W, 1.5, Draw.alpha(c, 0.6 * a));
        Draw.glow(g, Theme.W / 2.0, cy - 12, 320, c, 0.25 * a);
        var saved = g.getTransform();
        g.translate(Theme.W / 2.0, cy - 10);
        g.scale(0.8 + 0.2 * in, 0.8 + 0.2 * in);
        double tw = Draw.width(tf, title);
        Draw.outlined(g, title, -tw / 2, Draw.cap(tf) / 2, tf, Draw.alpha(c, a), Draw.alpha(Draw.RIM, a));
        g.setTransform(saved);
        Draw.center(g, sub, Theme.W / 2.0, cy + 42, sf, Draw.alpha(Theme.TEXT, a));
    }
}
