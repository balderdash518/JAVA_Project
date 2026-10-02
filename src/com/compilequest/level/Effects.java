package com.compilequest.level;

import com.compilequest.core.Json;
import com.compilequest.core.Sound.Sfx;
import com.compilequest.core.Theme;
import com.compilequest.entity.Bug;
import com.compilequest.entity.ErrorText;
import com.compilequest.world.TileMap;
import com.compilequest.world.TileType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Turns an outcome's effect list (from the level file) into timed steps that change the map.
 * Every tile change is logged so the next run can undo it.
 */
final class Effects {
    private Effects() {}

    private static final int T = TileMap.T;

    static List<Step> build(LevelScreen L, Map<String, Object> e) {
        String type = Json.str(e, "type", "");
        return switch (type) {
            case "BRIDGE" -> bridge(L, e);
            case "RAISE" -> raise(L, e);
            case "OPEN_DOOR" -> openDoor(L);
            case "SPAWN_BUGS" -> spawnBugs(L, e);
            case "SORT_PILLARS" -> sortPillars(L, e);
            case "ERROR_TEXT" -> errorText(L, e);
            case "SHATTER" -> shatter(L, e);
            default -> {
                System.err.println("Unknown effect type: " + type);
                yield new ArrayList<>();
            }
        };
    }

    private static char anchorChar(Map<String, Object> e, char def) {
        String s = Json.str(e, "anchor", "");
        return s.isEmpty() ? def : s.charAt(0);
    }

    private static List<Step> bridge(LevelScreen L, Map<String, Object> e) {
        List<Step> out = new ArrayList<>();
        int[] a = L.anchor(anchorChar(e, '_'));
        if (a == null) return out;
        int sx = a[0] + Json.integer(e, "dx", 0), sy = a[1] + Json.integer(e, "dy", 0);
        int len = Json.integer(e, "length", 1);
        boolean vertical = "V".equals(Json.str(e, "dir", "H"));
        boolean[] stopped = {false};
        for (int i = 0; i < len; i++) {
            int cx = vertical ? sx : sx + i, cy = vertical ? sy - i : sy;
            out.add(new Step(7, () -> {
                if (stopped[0]) return;
                if (L.map.get(cx, cy).solid) {
                    stopped[0] = true;
                    L.particles.sparks((cx + 0.5) * T, (cy + 0.5) * T, Theme.ERR, 12);
                    return;
                }
                L.map.set(cx, cy, TileType.BRIDGE, '=', true);
                L.map.pop[L.map.idx(cx, cy)] = 16;
                L.particles.burst((cx + 0.5) * T, (cy + 0.5) * T, "=", Theme.BRIDGE, 4, 2, 14);
                L.sfxVary(Sfx.POP, 0.7f);
                L.player.pushOut(L.map);
            }));
        }
        return out;
    }

    private static String liftLabel(int n, int width) {
        String s = width >= 3 ? "h=" + n : String.valueOf(n);
        StringBuilder sb = new StringBuilder(s);
        while (sb.length() < width) sb.append(' ');
        return sb.substring(0, width);
    }

    private static List<Step> raise(LevelScreen L, Map<String, Object> e) {
        List<Step> out = new ArrayList<>();
        int[] a = L.anchor(anchorChar(e, 'L'));
        if (a == null) return out;
        int width = 0;
        while (L.map.get(a[0] + width, a[1]) == TileType.LIFT) width++;
        int height = Json.integer(e, "height", 1);
        final int w = Math.max(1, width);
        for (int k = 1; k < height; k++) {
            final int row = a[1] - k;
            final int n = k + 1;
            out.add(new Step(10, () -> {
                for (int c = 0; c < w; c++) {
                    if (L.map.get(a[0] + c, row).solid) return;
                }
                String label = liftLabel(n, w);
                for (int c = 0; c < w; c++) {
                    L.map.setGlyph(a[0] + c, row + 1, '|', true);
                    L.map.set(a[0] + c, row, TileType.LIFT, label.charAt(c), true);
                    L.map.pop[L.map.idx(a[0] + c, row)] = 12;
                }
                L.particles.dust((a[0] + w / 2.0) * T, (row + 1) * T, 4);
                L.sfxVary(Sfx.POP, 0.7f);
                L.player.pushOut(L.map);
            }));
        }
        return out;
    }

    private static List<Step> openDoor(LevelScreen L) {
        List<Step> out = new ArrayList<>();
        boolean first = true;
        for (int y = 0; y < L.map.h; y++) {
            for (int x = 0; x < L.map.w; x++) {
                if (L.map.get(x, y) != TileType.DOOR) continue;
                final int cx = x, cy = y;
                final boolean sound = first;
                first = false;
                out.add(new Step(5, () -> {
                    L.map.set(cx, cy, TileType.EMPTY, ' ', true);
                    L.particles.burst((cx + 0.5) * T, (cy + 0.5) * T, "door", Theme.DOOR, 4, 2.5, 14);
                    if (sound) L.sfx(Sfx.OPEN);
                }));
            }
        }
        return out;
    }

    private static List<Step> spawnBugs(LevelScreen L, Map<String, Object> e) {
        List<Step> out = new ArrayList<>();
        List<int[]> spots = L.anchors('A');
        if (spots.isEmpty()) return out;
        int n = Json.integer(e, "count", 3);
        for (int i = 0; i < n; i++) {
            int[] s = spots.get(i % spots.size());
            double off = (i / spots.size()) * 44;
            final boolean alarm = i == 0;
            out.add(new Step(alarm ? 6 : 16, () -> {
                Bug b = new Bug(s[0] * T + (T - 36) / 2.0 + off, (s[1] + 1) * T - 28);
                b.fromRun = true;
                b.spawnAnim = 20;
                L.bugs.add(b);
                L.particles.burst(b.cx(), b.cy(), "!", Theme.ERR, 6, 3, 18);
                L.sfx(alarm ? Sfx.ALARM : Sfx.POP);
                if (alarm) L.cam.addShake(4);
            }));
        }
        return out;
    }

    private static List<Step> sortPillars(LevelScreen L, Map<String, Object> e) {
        List<Step> out = new ArrayList<>();
        if (L.pillarValues == null) return out;
        boolean ascending = !"<".equals(Json.str(e, "cmp", ">"));
        boolean overflow = "length".equals(Json.str(e, "bound", "length-1"));
        int[] v = L.pillarValues.clone();
        int n = v.length;
        outer:
        for (int i = 0; i < n - 1; i++) {
            int bound = overflow ? n : n - 1;
            for (int j = 0; j < bound; j++) {
                final int jj = j;
                if (j + 1 >= n) {
                    out.add(new Step(12, () -> L.highlightPillars(jj, 1)));
                    out.add(new Step(26, L::collapsePillars));
                    break outer;
                }
                out.add(new Step(9, () -> L.highlightPillars(jj, 2)));
                boolean swap = ascending ? v[j] > v[j + 1] : v[j] < v[j + 1];
                if (swap) {
                    int tmp = v[j];
                    v[j] = v[j + 1];
                    v[j + 1] = tmp;
                    out.add(new Step(13, () -> L.swapPillars(jj)));
                }
            }
        }
        return out;
    }

    private static List<Step> errorText(LevelScreen L, Map<String, Object> e) {
        List<Step> out = new ArrayList<>();
        int[] a = L.anchor(anchorChar(e, 'E'));
        if (a == null) return out;
        double x = (a[0] + 0.5 + Json.num(e, "dx", 0)) * T, y = (a[1] + 0.5 + Json.num(e, "dy", -2)) * T;
        String text = Json.str(e, "text", "error");
        out.add(new Step(6, () -> {
            L.errorTexts.add(new ErrorText(text, x, y));
            L.sfx(Sfx.ERROR);
            L.cam.addShake(4);
        }));
        return out;
    }

    private static List<Step> shatter(LevelScreen L, Map<String, Object> e) {
        List<Step> out = new ArrayList<>();
        int[] a = L.anchor(anchorChar(e, 'L'));
        if (a == null) return out;
        String text = Json.str(e, "text", "?");
        out.add(new Step(8, () -> {
            L.particles.burst((a[0] + 1.5) * T, (a[1] + 0.5) * T, text, Theme.NUMBER, 22, 5, 18);
            L.sfx(Sfx.BREAK);
            L.cam.addShake(6);
        }));
        return out;
    }

    /** Where the camera should look while this outcome plays. */
    static double[] focus(LevelScreen L, LevelConfig.Outcome out) {
        if (out.focus != 0) {
            int[] a = L.anchor(out.focus);
            if (a != null) return new double[] {(a[0] + 0.5) * T, (a[1] - 1) * T};
        }
        if (!out.effects.isEmpty()) {
            Map<String, Object> e = out.effects.get(0);
            String type = Json.str(e, "type", "");
            switch (type) {
                case "BRIDGE" -> {
                    int[] a = L.anchor(anchorChar(e, '_'));
                    if (a != null) {
                        int len = Json.integer(e, "length", 1);
                        boolean v = "V".equals(Json.str(e, "dir", "H"));
                        double x = a[0] + Json.integer(e, "dx", 0) + (v ? 0.5 : len / 2.0);
                        double y = a[1] + Json.integer(e, "dy", 0) - (v ? len / 2.0 : 2);
                        return new double[] {x * T, y * T};
                    }
                }
                case "RAISE" -> {
                    int[] a = L.anchor(anchorChar(e, 'L'));
                    if (a != null) return new double[] {(a[0] + 1.5) * T, (a[1] - Json.integer(e, "height", 1) / 2.0 - 1) * T};
                }
                case "OPEN_DOOR", "SPAWN_BUGS" -> {
                    int[] a = L.anchor(type.equals("OPEN_DOOR") ? 'D' : 'A');
                    if (a != null) return new double[] {(a[0] + 0.5) * T, (a[1] - 1) * T};
                }
                case "SORT_PILLARS" -> {
                    if (L.pillarValues != null) {
                        return new double[] {(L.pillarX + L.pillarValues.length * L.cfg.pillarUnit / 2.0) * T, (L.pillarY - 4) * T};
                    }
                }
                default -> {
                    int[] a = L.anchor(anchorChar(e, 'E'));
                    if (a != null) return new double[] {(a[0] + 0.5) * T, (a[1] - 1) * T};
                }
            }
        }
        return new double[] {L.exit.cx(), L.exit.cy()};
    }
}
