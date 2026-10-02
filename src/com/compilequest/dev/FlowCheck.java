package com.compilequest.dev;

import com.compilequest.core.Game;
import com.compilequest.core.Input;
import com.compilequest.editor.EditorOverlay;
import com.compilequest.level.LevelConfig;
import com.compilequest.level.LevelScreen;

import java.awt.event.KeyEvent;
import java.awt.geom.Rectangle2D;
import java.lang.reflect.Field;
import java.util.Map;

/**
 * Developer tool: drives the real game objects through the main flows (running programs,
 * editor drag and drop, death, level clear) and checks the results.
 * Run with: java -cp out com.compilequest.Main --flowcheck
 */
public final class FlowCheck {
    private FlowCheck() {}

    private static int failures;

    private static void check(boolean ok, String what) {
        System.out.println((ok ? "  ok   " : "  FAIL ") + what);
        if (!ok) failures++;
    }

    public static void run() throws Exception {
        Game game = new Game(true);
        for (int i = 0; i < game.levels.size(); i++) runs(game, i);
        editor(game);
        death(game);
        clear(game);
        System.out.println(failures == 0 ? "\nALL FLOWS PASSED" : "\n" + failures + " FLOW CHECK(S) FAILED");
    }

    private static void step(Game game, LevelScreen L, int frames) {
        for (int i = 0; i < frames; i++) {
            game.input.poll();
            L.update(game);
        }
    }

    private static void runUntilDone(Game game, LevelScreen L) {
        for (int i = 0; i < 2000 && L.running(); i++) step(game, L, 1);
    }

    private static void assign(LevelScreen L, LevelConfig.Outcome o) {
        L.assignment.clear();
        for (Map.Entry<String, String> e : o.when.entrySet()) {
            if (!e.getKey().startsWith("@")) L.assignment.put(e.getKey(), e.getValue());
        }
        for (String s : L.cfg.slotOrder) L.assignment.putIfAbsent(s, L.cfg.blocks.keySet().iterator().next());
        L.hasKey = "yes".equals(o.when.get("@key"));
    }

    private static void runs(Game game, int index) {
        LevelConfig cfg = game.levels.get(index);
        System.out.println("\n== " + cfg.file + " ==");
        for (LevelConfig.Outcome o : cfg.outcomes) {
            LevelScreen L = new LevelScreen(game, index);
            L.inventory.addAll(cfg.blocks.values());
            assign(L, o);
            step(game, L, 5);
            L.startRun();
            check(L.running(), "run starts for " + o.when);
            runUntilDone(game, L);
            check(!L.running(), "run finishes");
            if (o.success) {
                check(L.solved && L.exit.active, "success opens the exit");
                check(L.player.hp == 5, "success costs no Memory");
            } else {
                check(!L.solved && !L.exit.active, "failure keeps the exit closed");
                check(L.player.hp == 4, "failure costs 1 Memory (hp=" + L.player.hp + ")");
                check(!L.console.isEmpty() && L.console.get(L.console.size() - 1).memory(), "console reports the error");
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Rectangle2D.Double> rects(EditorOverlay e, String field) throws Exception {
        Field f = EditorOverlay.class.getDeclaredField(field);
        f.setAccessible(true);
        return (Map<String, Rectangle2D.Double>) f.get(e);
    }

    private static void drag(Game game, LevelScreen L, Rectangle2D.Double from, Rectangle2D.Double to) {
        Input in = game.input;
        double fx = from.getCenterX(), fy = from.getCenterY(), tx = to.getCenterX(), ty = to.getCenterY();
        in.injectMouse(fx, fy);
        step(game, L, 1);
        in.injectButton(Input.LEFT, true, fx, fy);
        step(game, L, 1);
        for (int k = 1; k <= 6; k++) {
            in.injectMouse(fx + (tx - fx) * k / 6, fy + (ty - fy) * k / 6);
            step(game, L, 1);
        }
        in.injectButton(Input.LEFT, false, tx, ty);
        step(game, L, 2);
    }

    private static void press(Game game, LevelScreen L, int key) {
        game.input.injectKey(key, true);
        step(game, L, 1);
        game.input.injectKey(key, false);
        step(game, L, 1);
    }

    private static void editor(Game game) throws Exception {
        System.out.println("\n== Editor drag and drop (Level3) ==");
        LevelScreen L = new LevelScreen(game, 3);
        L.inventory.addAll(L.cfg.blocks.values());
        L.openEditor(true);
        step(game, L, 12);
        EditorOverlay e = L.editor();
        drag(game, L, rects(e, "invRects").get("lt"), rects(e, "slotRects").get("op"));
        check("lt".equals(L.assignment.get("op")), "drag '<' into the operator slot");
        drag(game, L, rects(e, "invRects").get("print"), rects(e, "slotRects").get("op"));
        check("lt".equals(L.assignment.get("op")), "a LINE block is rejected by a TOKEN slot");
        drag(game, L, rects(e, "invRects").get("n8"), rects(e, "slotRects").get("n"));
        drag(game, L, rects(e, "slotRects").get("op"), rects(e, "slotRects").get("n"));
        check("n8".equals(L.assignment.get("op")) && "lt".equals(L.assignment.get("n")), "dropping on a filled slot swaps the blocks");
        Rectangle2D.Double slot = rects(e, "slotRects").get("op");
        game.input.injectButton(Input.RIGHT, true, slot.getCenterX(), slot.getCenterY());
        step(game, L, 1);
        game.input.injectButton(Input.RIGHT, false, slot.getCenterX(), slot.getCenterY());
        step(game, L, 1);
        check(!L.assignment.containsKey("op"), "right-click returns a block to the inventory");
        press(game, L, KeyEvent.VK_ESCAPE);
        check(L.mode == LevelScreen.Mode.PLAYING, "Esc closes the editor");
        press(game, L, KeyEvent.VK_TAB);
        check(L.mode == LevelScreen.Mode.EDITOR, "Tab opens the editor");
        press(game, L, KeyEvent.VK_TAB);
        check(L.mode == LevelScreen.Mode.PLAYING, "Tab closes the editor");
    }

    private static void death(Game game) {
        System.out.println("\n== Death and respawn ==");
        LevelScreen L = new LevelScreen(game, 0);
        step(game, L, 3);
        L.player.hp = 1;
        LevelConfig.Outcome fail = L.cfg.outcomes.stream().filter(o -> !o.success).findFirst().orElseThrow();
        L.inventory.addAll(L.cfg.blocks.values());
        assign(L, fail);
        L.startRun();
        runUntilDone(game, L);
        check(L.mode == LevelScreen.Mode.DEAD, "a failed run at 1 Memory ends in OutOfMemoryError");
        step(game, L, 200);
        press(game, L, KeyEvent.VK_SPACE);
        step(game, L, 60);
        check(L.mode == LevelScreen.Mode.PLAYING && L.player.hp == 5, "any key catches and respawns with full Memory");
        check(!L.inventory.isEmpty(), "collected blocks are kept after death");
    }

    private static void clear(Game game) {
        System.out.println("\n== Level clear ==");
        LevelScreen L = new LevelScreen(game, 0);
        LevelConfig.Outcome win = L.cfg.outcomes.stream().filter(o -> o.success).findFirst().orElseThrow();
        L.inventory.addAll(L.cfg.blocks.values());
        assign(L, win);
        L.startRun();
        runUntilDone(game, L);
        L.player.x = L.exit.cx() - 11;
        L.player.y = L.exit.y + L.exit.h - 44;
        step(game, L, 3);
        check(L.mode == LevelScreen.Mode.CLEARED, "touching the open exit clears the level");
        check(game.save.rec(0).cleared && game.save.unlocked >= 1, "progress is saved and Level1 unlocks");
    }
}
