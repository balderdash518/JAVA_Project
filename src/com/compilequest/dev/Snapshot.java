package com.compilequest.dev;

import com.compilequest.core.Game;
import com.compilequest.core.GameWindow;
import com.compilequest.core.Save;
import com.compilequest.core.Screen;
import com.compilequest.core.Theme;
import com.compilequest.entity.Player;
import com.compilequest.level.LevelConfig;
import com.compilequest.level.LevelScreen;
import com.compilequest.ui.MenuScreen;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

/**
 * Developer tool: renders key screens to PNG files without opening a window.
 * Run with: java -cp out com.compilequest.Main --snapshot <folder>
 */
public final class Snapshot {
    private Snapshot() {}

    public static void run(String folder) throws Exception {
        Path out = Paths.get(folder);
        Files.createDirectories(out);
        Game game = new Game(true);

        // Menu with some progress.
        game.save.unlocked = Math.min(3, game.levels.size() - 1);
        for (int i = 0; i < Math.min(3, game.levels.size()); i++) {
            Save.Rec r = game.save.rec(i);
            r.cleared = true;
            r.bestMs = 60_000 + i * 31_000;
            r.stars[0] = true;
            if (i == 0) r.stars[1] = r.stars[2] = true;
        }
        MenuScreen menu = new MenuScreen(game, Math.min(3, game.levels.size() - 1));
        step(menu, game, 40);
        shot(menu, out.resolve("menu.png"));

        for (int i = 0; i < game.levels.size(); i++) {
            LevelScreen L = new LevelScreen(game, i);
            step(L, game, 230);
            shot(L, out.resolve("level" + i + "_start.png"));
            overview(L, out.resolve("map" + i + ".png"));
        }

        int demo = Math.min(3, game.levels.size() - 1);
        LevelConfig cfg = game.levels.get(demo);

        // Editor with everything collected and some slots filled.
        LevelScreen L = new LevelScreen(game, demo);
        L.inventory.addAll(cfg.blocks.values());
        LevelConfig.Outcome win = winning(cfg);
        int k = 0;
        for (Map.Entry<String, String> e : win.when.entrySet()) {
            if (e.getKey().startsWith("@")) continue;
            if (k++ % 2 == 0) L.assignment.put(e.getKey(), e.getValue());
        }
        moveToTerminal(L);
        L.openEditor(true);
        step(L, game, 20);
        shot(L, out.resolve("editor.png"));

        // A correct run.
        L.assignment.clear();
        for (Map.Entry<String, String> e : win.when.entrySet()) if (!e.getKey().startsWith("@")) L.assignment.put(e.getKey(), e.getValue());
        if ("yes".equals(win.when.get("@key"))) L.hasKey = true;
        L.startRun();
        step(L, game, 120);
        shot(L, out.resolve("run_exec.png"));
        for (int i = 0; i < 400 && L.running(); i++) {
            step(L, game, 1);
            if (i == 140) shot(L, out.resolve("run_result.png"));
        }

        // A failed run.
        LevelScreen F = new LevelScreen(game, demo);
        F.inventory.addAll(cfg.blocks.values());
        LevelConfig.Outcome lose = cfg.outcomes.stream().filter(o -> !o.success).findFirst().orElse(cfg.defaultFailure);
        for (String s : cfg.slotOrder) {
            String v = lose.when.get(s);
            if (v == null) v = win.when.get(s);
            if (v == null) v = cfg.blocks.keySet().iterator().next();
            F.assignment.put(s, v);
        }
        moveToTerminal(F);
        F.startRun();
        for (int i = 0; i < 400 && F.running(); i++) {
            step(F, game, 1);
            if (i == 200) shot(F, out.resolve("run_fail.png"));
        }
        step(F, game, 10);
        shot(F, out.resolve("after_fail.png"));

        // A double jump in mid-air.
        LevelScreen J = new LevelScreen(game, 0);
        step(J, game, 230);
        for (int f = 0; f < 21; f++) {
            com.compilequest.entity.Player.Controls c = new com.compilequest.entity.Player.Controls();
            c.right = true;
            c.jumpHeld = true;
            c.jumpPressed = f == 0 || f == 16;
            J.updateWorld(c);
        }
        shot(J, out.resolve("double_jump.png"));

        // Pause, death and clear.
        LevelScreen P = new LevelScreen(game, 0);
        step(P, game, 210);
        P.forcePause();
        step(P, game, 12);
        shot(P, out.resolve("pause.png"));

        LevelScreen D = new LevelScreen(game, 0);
        step(D, game, 5);
        D.die("NullSpike.onTouch");
        step(D, game, 170);
        shot(D, out.resolve("death.png"));

        LevelScreen C = new LevelScreen(game, 0);
        step(C, game, 5);
        C.stars[0] = C.stars[2] = true;
        C.playFrames = 60 * 151;
        C.forceClear();
        step(C, game, 110);
        shot(C, out.resolve("clear.png"));

        System.out.println("Snapshots written to " + out.toAbsolutePath());
    }

    private static LevelConfig.Outcome winning(LevelConfig cfg) {
        return cfg.outcomes.stream().filter(o -> o.success).findFirst().orElseThrow();
    }

    private static void moveToTerminal(LevelScreen L) {
        if (L.terminals.isEmpty()) return;
        var t = L.terminals.get(0);
        L.player.x = t.x - Player.W - 4;
        L.player.y = t.y + t.h - Player.H;
        L.cam.snap(L.player.cx(), L.player.cy());
    }

    private static void step(Screen s, Game game, int frames) {
        for (int i = 0; i < frames; i++) {
            game.input.poll();
            s.update(game);
        }
    }

    private static void shot(Screen s, Path file) throws Exception {
        BufferedImage img = new BufferedImage(Theme.W, Theme.H, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        GameWindow.hints(g);
        s.render(g);
        g.dispose();
        ImageIO.write(img, "png", file.toFile());
    }

    private static void overview(LevelScreen L, Path file) throws Exception {
        int w = L.map.w * 32, h = L.map.h * 32;
        double scale = Math.min(1.0, 2600.0 / w);
        BufferedImage img = new BufferedImage((int) (w * scale), (int) (h * scale), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        GameWindow.hints(g);
        g.scale(scale, scale);
        L.renderOverview(g);
        g.dispose();
        ImageIO.write(img, "png", file.toFile());
    }
}
