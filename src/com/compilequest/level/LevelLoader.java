package com.compilequest.level;

import com.compilequest.core.Res;
import com.compilequest.editor.BlockDef;
import com.compilequest.entity.Bug;
import com.compilequest.entity.Checkpoint;
import com.compilequest.entity.ExceptionTurret;
import com.compilequest.entity.Exit;
import com.compilequest.entity.MovingPlatform;
import com.compilequest.entity.Pickup;
import com.compilequest.entity.Player;
import com.compilequest.entity.Terminal;
import com.compilequest.world.TileMap;
import com.compilequest.world.TileType;

import java.util.ArrayList;

/** Builds a level from its character map (legend in DESIGN.md 5.1 and 8.2). */
final class LevelLoader {
    private LevelLoader() {}

    private static final int T = TileMap.T;
    private static final String ANCHORS = "_LDQAEJSP";

    static void load(LevelScreen L) {
        LevelConfig cfg = L.cfg;
        int h = cfg.map.size();
        int w = 1;
        for (String row : cfg.map) w = Math.max(w, row.length());
        TileMap m = new TileMap(w, h);
        L.map = m;
        m.wallText = wallText(cfg.wallText);
        int wallIdx = 0;
        int star = 0;
        L.spawnX = 2 * T;
        L.spawnY = T;

        for (int y = 0; y < h; y++) {
            String row = cfg.map.get(y);
            for (int x = 0; x < w; x++) {
                char c = x < row.length() ? row.charAt(x) : ' ';
                double cx = x * T + T / 2.0, cy = y * T + T / 2.0;
                switch (c) {
                    case ' ' -> { }
                    case '#' -> {
                        m.set(x, y, TileType.SOLID, ' ', false);
                        m.off[m.idx(x, y)] = wallIdx;
                        wallIdx += 3;
                    }
                    case '-' -> m.set(x, y, TileType.COMMENT, ' ', false);
                    case '+' -> m.set(x, y, TileType.SPRING, ' ', false);
                    case '^' -> m.set(x, y, TileType.SPIKE, ' ', false);
                    case 'v' -> m.set(x, y, TileType.SPIKE_DOWN, ' ', false);
                    case 'B' -> m.set(x, y, TileType.BREAK, ' ', false);
                    case 'T' -> m.set(x, y, TileType.TRANSIENT, ' ', false);
                    case 'V' -> m.set(x, y, TileType.VOLATILE, ' ', false);
                    case 'F' -> m.set(x, y, TileType.FINAL, ' ', false);
                    case '~' -> m.set(x, y, TileType.WATER, '~', false);
                    case '_' -> m.set(x, y, TileType.HINT, ' ', false);
                    case 'L' -> m.set(x, y, TileType.LIFT, ' ', false);
                    case 'D' -> m.set(x, y, TileType.DOOR, ' ', false);
                    case 'Q', 'A', 'M' -> m.set(x, y, TileType.ANCHOR, ' ', false);
                    case 'S' -> L.checkpoints.add(new Checkpoint(x, y));
                    case 'E' -> L.exit = new Exit(x, y);
                    case 'J' -> L.terminals.add(new Terminal(x, y));
                    case 'P' -> {
                        L.spawnX = x * T + (T - Player.W) / 2;
                        L.spawnY = (y + 1) * T - Player.H;
                    }
                    case 'G' -> L.pickups.add(new Pickup(Pickup.Kind.GC, cx, cy, null, -1));
                    case '*' -> L.pickups.add(new Pickup(Pickup.Kind.STAR, cx, cy, null, Math.min(2, star++)));
                    case 'K' -> L.pickups.add(new Pickup(Pickup.Kind.KEY, cx, cy, null, -1));
                    case 'b' -> L.bugs.add(new Bug(x * T + (T - 36) / 2.0, (y + 1) * T - 28));
                    case 'x' -> L.turrets.add(new ExceptionTurret(cx, (y + 1) * T));
                    default -> {
                        String id = cfg.pickups.get(c);
                        if (id != null) {
                            BlockDef b = cfg.blocks.get(id);
                            L.pickups.add(new Pickup(Pickup.Kind.BLOCK, cx, cy, b, -1));
                        } else {
                            System.err.println(cfg.file + ": unknown map character '" + c + "' at " + x + "," + y);
                        }
                    }
                }
                if (ANCHORS.indexOf(c) >= 0) L.anchors.computeIfAbsent(c, k -> new ArrayList<>()).add(new int[] {x, y});
            }
        }
        if (L.exit == null) throw new IllegalStateException(cfg.file + ": the map has no exit 'E'");

        // Moving platforms: each run of 'M' is one platform, paired with movers[] in reading order.
        int mover = 0;
        for (int y = 0; y < h; y++) {
            String row = cfg.map.get(y);
            for (int x = 0; x < row.length(); x++) {
                if (row.charAt(x) != 'M' || (x > 0 && row.charAt(x - 1) == 'M')) continue;
                int len = 0;
                while (x + len < row.length() && row.charAt(x + len) == 'M') len++;
                double[] path = mover < cfg.movers.size() ? cfg.movers.get(mover) : new double[] {4, 0, 240};
                mover++;
                for (int k = 0; k < len; k++) m.set(x + k, y, TileType.EMPTY, ' ', false);
                L.platforms.add(new MovingPlatform(x, y, len, path[0], path[1], (int) path[2]));
            }
        }

        // Comment platforms take their text from comments[] in reading order.
        int comment = 0;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (m.get(x, y) != TileType.COMMENT || m.get(x - 1, y) == TileType.COMMENT) continue;
                String text = comment < cfg.comments.size() ? cfg.comments.get(comment) : "//";
                comment++;
                m.commentText.put(m.idx(x, y), text);
            }
        }

        // The lift starts one tile tall and shows "h=?".
        int[] lift = L.anchor('L');
        if (lift != null) {
            int width = 0;
            while (m.get(lift[0] + width, lift[1]) == TileType.LIFT) width++;
            String label = width >= 3 ? "h=?" : "?";
            for (int k = 0; k < width; k++) m.glyph[m.idx(lift[0] + k, lift[1])] = k < label.length() ? label.charAt(k) : ' ';
        }

        // Pillars for the sorting level.
        int[] q = L.anchor('Q');
        if (q != null && cfg.pillars != null) {
            L.pillarX = q[0];
            L.pillarY = q[1];
            L.pillarValues = cfg.pillars.clone();
            L.pillarInitial = cfg.pillars.clone();
            int max = 0;
            for (int v : cfg.pillars) max = Math.max(max, v);
            L.pillarMaxH = max * cfg.pillarUnit;
            for (int i = 0; i < cfg.pillars.length; i++) L.buildPillar(i, cfg.pillars[i], false);
        }

        L.player = new Player(L.spawnX, L.spawnY);
    }

    /** The source text that fills solid walls, with every run of whitespace collapsed to one space. */
    private static String wallText(String name) {
        String raw = Res.exists("text/" + name) ? Res.text("text/" + name) : "public class Wall { }";
        StringBuilder sb = new StringBuilder(raw.length());
        boolean space = false;
        for (char c : raw.toCharArray()) {
            if (Character.isWhitespace(c)) {
                space = sb.length() > 0;
                continue;
            }
            if (c >= 128) continue;
            if (space) sb.append(' ');
            space = false;
            sb.append(c);
        }
        return sb.length() == 0 ? "###" : sb.toString();
    }
}
