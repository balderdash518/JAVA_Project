package com.compilequest.dev;

import com.compilequest.core.Game;
import com.compilequest.entity.MovingPlatform;
import com.compilequest.entity.Pickup;
import com.compilequest.entity.Player;
import com.compilequest.entity.Terminal;
import com.compilequest.level.LevelConfig;
import com.compilequest.level.LevelScreen;
import com.compilequest.world.TileMap;
import com.compilequest.world.TileType;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Developer tool: explores each level with the real player physics (walks, drops and many
 * kinds of jumps from every reachable standing spot) and reports what can be reached.
 * Run with: java -cp out com.compilequest.Main --mapcheck
 */
public final class MapCheck {
    private MapCheck() {}

    private static final int T = TileMap.T;

    public static void run() {
        Game game = new Game(true);
        boolean ok = true;
        for (int i = 0; i < game.levels.size(); i++) ok &= check(game, i);
        System.out.println(ok ? "\nALL CHECKS PASSED" : "\nSOME CHECKS FAILED");
    }

    /** What one exploration could touch. */
    static final class Reach {
        final Set<Pickup> pickups = new HashSet<>();
        boolean terminal, exit;
        int nodes;
    }

    private static final class Host implements Player.Host {
        final TileMap map;
        final List<MovingPlatform> plats;
        boolean hazard;

        Host(TileMap map, List<MovingPlatform> plats) {
            this.map = map;
            this.plats = plats;
        }

        @Override public TileMap map() { return map; }
        @Override public List<MovingPlatform> platforms() { return plats; }
        @Override public void onSpike(Player p, int tx, int ty) { hazard = true; }
        @Override public void onWater(Player p) { hazard = true; }
        @Override public void onFallOut(Player p) { hazard = true; }
    }

    private static boolean check(Game game, int index) {
        LevelConfig cfg = game.levels.get(index);
        System.out.println("\n== " + cfg.file + " ==");
        boolean ok = true;

        LevelScreen L = fresh(game, index);
        Reach pre = explore(L, starts(L));
        System.out.println("  standing spots explored: " + pre.nodes);
        for (Pickup p : L.pickups) {
            boolean r = pre.pickups.contains(p);
            String name = switch (p.kind) {
                case BLOCK -> "block " + p.block.id() + "  " + p.block.text();
                case STAR -> "star #" + (p.starIndex + 1);
                default -> p.kind.toString().toLowerCase();
            };
            System.out.println("  " + (r ? "ok   " : "MISS ") + name);
            if (!r && p.kind != Pickup.Kind.STAR) ok = false;
        }
        System.out.println("  " + (pre.terminal ? "ok   " : "MISS ") + "javac terminal");
        ok &= pre.terminal;
        // Level 0 has an open exit area on purpose; later levels must be gated by the program.
        boolean gated = index > 0;
        if (gated) {
            System.out.println("  " + (pre.exit ? "FAIL " : "ok   ") + "exit area out of reach before the program runs");
            ok &= !pre.exit;
        } else {
            System.out.println("  info exit reachable before running: " + pre.exit);
        }

        for (int k = 0; k < cfg.outcomes.size(); k++) {
            LevelConfig.Outcome o = cfg.outcomes.get(k);
            LevelScreen A = fresh(game, index);
            A.hasKey = "yes".equals(o.when.get("@key"));
            A.applyInstant(o);
            List<double[]> st = starts(A);
            st.addAll(terminalStarts(A));
            Reach r = explore(A, st);
            String tag = "outcome " + (k + 1) + " " + o.when + (o.success ? " [success]" : " [failure]");
            if (o.success) {
                System.out.println("  " + (r.exit ? "ok   " : "FAIL ") + tag + " -> exit reachable: " + r.exit);
                ok &= r.exit;
                for (Pickup p : A.pickups) {
                    if (p.kind == Pickup.Kind.STAR && !pre.pickups.contains(p)) {
                        boolean later = r.pickups.contains(p);
                        System.out.println("       star #" + (p.starIndex + 1) + " after success: " + (later ? "reachable" : "MISSING"));
                    }
                }
            } else {
                boolean good = r.terminal && !(gated && r.exit);
                System.out.println("  " + (good ? "ok   " : "FAIL ") + tag + " -> terminal still reachable: " + r.terminal
                        + ", exit reachable: " + r.exit);
                ok &= good;
            }
        }
        return ok;
    }

    private static LevelScreen fresh(Game game, int index) {
        LevelScreen L = new LevelScreen(game, index);
        // Break blocks can always be shot, so treat them as open.
        for (int y = 0; y < L.map.h; y++) {
            for (int x = 0; x < L.map.w; x++) {
                if (L.map.get(x, y) == TileType.BREAK) L.map.set(x, y, TileType.EMPTY, ' ', false);
            }
        }
        return L;
    }

    private static List<double[]> starts(LevelScreen L) {
        List<double[]> s = new ArrayList<>();
        s.add(new double[] {L.spawnX, L.spawnY});
        return s;
    }

    private static List<double[]> terminalStarts(LevelScreen L) {
        List<double[]> s = new ArrayList<>();
        for (Terminal t : L.terminals) s.add(new double[] {t.x - Player.W - 2, t.y + t.h - Player.H});
        return s;
    }

    private static Reach explore(LevelScreen L, List<double[]> starts) {
        List<MovingPlatform> plats = new ArrayList<>();
        for (MovingPlatform p : L.platforms) {
            plats.add(p.at(0));
            plats.add(p.at(0.5));
            plats.add(p.at(1));
        }
        Host host = new Host(L.map, plats);
        Reach reach = new Reach();
        ArrayDeque<double[]> queue = new ArrayDeque<>();
        Set<Long> seen = new HashSet<>();
        for (double[] s : starts) {
            double[] settled = settle(host, s[0], s[1]);
            if (settled != null && seen.add(key(settled))) queue.add(settled);
        }
        while (!queue.isEmpty()) {
            double[] n = queue.poll();
            reach.nodes++;
            for (int[] a : ACTIONS) {
                double[] land = simulate(host, L, n[0], n[1], a, reach);
                if (land != null && seen.add(key(land))) queue.add(land);
            }
            if (reach.nodes > 6000) break;
        }
        return reach;
    }

    private static long key(double[] n) {
        long bx = Math.round(n[0] / 16);
        long by = Math.round(n[1] + Player.H);
        return (bx << 32) ^ (by & 0xffffffffL);
    }

    /** Drops the player at (x, y) and lets them settle; returns the standing position. */
    private static double[] settle(Host host, double x, double y) {
        Player p = new Player(x, y);
        host.hazard = false;
        Player.Controls none = new Player.Controls();
        for (int f = 0; f < 120; f++) {
            p.update(host, none);
            if (host.hazard) return null;
            if (p.onGround && Math.abs(p.vx) < 0.05) return new double[] {p.x, p.y};
        }
        return null;
    }

    /** Action: {kind, dir, initFast, jumpHold or second-jump frame, dirDelay}. kind 0 walk, 1 jump, 2 drop, 3 double jump. */
    private static final List<int[]> ACTIONS = new ArrayList<>();

    static {
        ACTIONS.add(new int[] {0, -1, 0, 0, 0});
        ACTIONS.add(new int[] {0, 1, 0, 0, 0});
        ACTIONS.add(new int[] {2, 0, 0, 0, 0});
        for (int hold : new int[] {3, 8, 40}) ACTIONS.add(new int[] {1, 0, 0, hold, 0});
        for (int dir : new int[] {-1, 1}) {
            for (int fast : new int[] {0, 1}) {
                for (int hold : new int[] {3, 8, 40}) {
                    for (int delay : new int[] {0, 10, 20}) ACTIONS.add(new int[] {1, dir, fast, hold, delay});
                }
            }
        }
        for (int second : new int[] {6, 12, 18, 24}) ACTIONS.add(new int[] {3, 0, 0, second, 0});
        for (int dir : new int[] {-1, 1}) {
            for (int fast : new int[] {0, 1}) {
                for (int second : new int[] {6, 12, 18, 24, 32}) ACTIONS.add(new int[] {3, dir, fast, second, 0});
            }
        }
    }

    private static double[] simulate(Host host, LevelScreen L, double x, double y, int[] a, Reach reach) {
        Player p = new Player(x, y);
        host.hazard = false;
        Player.Controls none = new Player.Controls();
        p.update(host, none);
        if (!p.onGround) return null;
        if (a[2] == 1) p.vx = a[1] * 4.0;
        boolean airborne = false;
        double startX = p.x;
        for (int f = 0; f < 260; f++) {
            Player.Controls c = new Player.Controls();
            switch (a[0]) {
                case 0 -> {
                    boolean moving = Math.abs(p.x - startX) < T;
                    c.left = moving && a[1] < 0;
                    c.right = moving && a[1] > 0;
                }
                case 1 -> {
                    c.jumpPressed = f == 0;
                    c.jumpHeld = f < a[3];
                    c.jumpReleased = f == a[3];
                    boolean dir = f >= a[4] && (!airborne || !p.onGround);
                    c.left = dir && a[1] < 0;
                    c.right = dir && a[1] > 0;
                }
                case 3 -> {
                    c.jumpPressed = f == 0 || f == a[3];
                    c.jumpHeld = true;
                    c.left = a[1] < 0;
                    c.right = a[1] > 0;
                }
                default -> {
                    c.downPressed = f == 0;
                    c.downHeld = f < 4;
                }
            }
            p.update(host, c);
            if (host.hazard) return null;
            touch(L, p, reach);
            if (!p.onGround) airborne = true;
            boolean done = switch (a[0]) {
                case 0 -> Math.abs(p.x - startX) >= T && p.onGround && Math.abs(p.vx) < 0.05;
                case 1, 2, 3 -> airborne && p.onGround && f > 1;
                default -> false;
            };
            if (a[0] == 0 && airborne && p.onGround) done = true;
            if (done) {
                if (a[0] == 0 && Math.abs(p.x - startX) < 1) return null;
                return new double[] {p.x, p.y};
            }
        }
        return null;
    }

    private static void touch(LevelScreen L, Player p, Reach reach) {
        for (Pickup pk : L.pickups) if (!reach.pickups.contains(pk) && p.overlaps(pk)) reach.pickups.add(pk);
        if (!reach.terminal) for (Terminal t : L.terminals) if (t.inReach(p)) reach.terminal = true;
        if (!reach.exit && p.overlaps(L.exit)) reach.exit = true;
    }
}
