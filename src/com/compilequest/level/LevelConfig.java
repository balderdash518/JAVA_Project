package com.compilequest.level;

import com.compilequest.core.Json;
import com.compilequest.core.Res;
import com.compilequest.editor.BlockDef;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Everything about one level that lives in levels/levelN.json and levels/levelN.txt. */
public final class LevelConfig {
    public static final Pattern SLOT = Pattern.compile("\\{\\{(\\w+)\\}\\}");

    /** One possible result of running the program: which blocks match, and what it does to the map. */
    public static final class Outcome {
        public final Map<String, String> when = new LinkedHashMap<>();
        public boolean success;
        public String output = "";
        public boolean outputAfter;
        public String message = "";
        public String error = "";
        public char focus;
        public final List<Map<String, Object>> effects = new ArrayList<>();
    }

    public int index;
    public String file = "";
    public String title = "";
    public String task = "";
    public List<String> news = new ArrayList<>();
    public List<String> map = new ArrayList<>();
    public String wallText = "wall.txt";
    public List<String> comments = new ArrayList<>();
    public final LinkedHashMap<String, BlockDef> blocks = new LinkedHashMap<>();
    public final Map<Character, String> pickups = new HashMap<>();
    public List<String> skeleton = new ArrayList<>();
    public final LinkedHashMap<String, BlockDef.Kind> slots = new LinkedHashMap<>();
    public final List<String> slotOrder = new ArrayList<>();
    public final Map<String, Integer> slotLine = new HashMap<>();
    public final List<Outcome> outcomes = new ArrayList<>();
    public Outcome defaultFailure = new Outcome();
    public final List<double[]> movers = new ArrayList<>();
    public int[] pillars;
    public int pillarUnit = 2;
    public int starCount;
    public boolean hasKey;

    public static List<LevelConfig> loadAll() {
        List<LevelConfig> list = new ArrayList<>();
        for (int i = 0; Res.exists("levels/level" + i + ".json"); i++) list.add(load(i));
        return list;
    }

    public static LevelConfig load(int index) {
        String name = "levels/level" + index + ".json";
        Map<String, Object> j;
        try {
            j = Json.obj(Json.parse(Res.text(name)));
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(name + ": " + e.getMessage(), e);
        }
        LevelConfig c = new LevelConfig();
        c.index = index;
        c.file = Json.str(j, "file", "Level" + index + ".java");
        c.title = Json.str(j, "title", "");
        c.task = Json.str(j, "task", "");
        c.news = Json.strings(j.get("new"));
        List<String> rows = new ArrayList<>(Arrays.asList(Res.text(Json.str(j, "map", "levels/level" + index + ".txt")).split("\n", -1)));
        while (!rows.isEmpty() && rows.get(rows.size() - 1).isBlank()) rows.remove(rows.size() - 1);
        c.map = rows;
        c.wallText = Json.str(j, "wallText", "wall.txt");
        c.comments = Json.strings(j.get("comments"));
        for (Object o : Json.arr(j.get("blocks"))) {
            Map<String, Object> b = Json.obj(o);
            BlockDef d = new BlockDef(Json.str(b, "id", ""), BlockDef.Kind.valueOf(Json.str(b, "kind", "LINE")), Json.str(b, "text", ""));
            c.blocks.put(d.id(), d);
        }
        for (Map.Entry<String, Object> e : Json.obj(j.get("pickups")).entrySet()) {
            c.pickups.put(e.getKey().charAt(0), String.valueOf(e.getValue()));
        }
        c.skeleton = Json.strings(j.get("skeleton"));
        for (Map.Entry<String, Object> e : Json.obj(j.get("slots")).entrySet()) {
            c.slots.put(e.getKey(), BlockDef.Kind.valueOf(Json.str(Json.obj(e.getValue()), "kind", "LINE")));
        }
        for (int i = 0; i < c.skeleton.size(); i++) {
            Matcher m = SLOT.matcher(c.skeleton.get(i));
            while (m.find()) {
                c.slotOrder.add(m.group(1));
                c.slotLine.put(m.group(1), i + 1);
            }
        }
        for (Object o : Json.arr(j.get("outcomes"))) c.outcomes.add(outcome(Json.obj(o)));
        if (j.containsKey("defaultFailure")) {
            c.defaultFailure = outcome(Json.obj(j.get("defaultFailure")));
        } else {
            c.defaultFailure.message = "error: the program does not do what the task needs";
            c.defaultFailure.error = "error: build failed";
        }
        for (Object o : Json.arr(j.get("movers"))) {
            Map<String, Object> m = Json.obj(o);
            c.movers.add(new double[] {Json.num(m, "dx", 0), Json.num(m, "dy", 0), Json.num(m, "period", 240)});
        }
        Map<String, Object> p = Json.obj(j.get("pillars"));
        if (!p.isEmpty()) {
            List<Object> vals = Json.arr(p.get("values"));
            c.pillars = new int[vals.size()];
            for (int i = 0; i < vals.size(); i++) c.pillars[i] = ((Number) vals.get(i)).intValue();
            c.pillarUnit = Json.integer(p, "unit", 2);
        }
        for (String row : c.map) {
            for (char ch : row.toCharArray()) {
                if (ch == '*') c.starCount++;
                if (ch == 'K') c.hasKey = true;
            }
        }
        c.validate(name);
        return c;
    }

    private static Outcome outcome(Map<String, Object> m) {
        Outcome o = new Outcome();
        for (Map.Entry<String, Object> e : Json.obj(m.get("when")).entrySet()) o.when.put(e.getKey(), String.valueOf(e.getValue()));
        o.success = Json.bool(m, "success", false);
        o.output = Json.str(m, "output", "");
        o.outputAfter = Json.bool(m, "outputAfter", false);
        o.message = Json.str(m, "message", "");
        o.error = Json.str(m, "error", "");
        String f = Json.str(m, "focus", "");
        o.focus = f.isEmpty() ? 0 : f.charAt(0);
        for (Object e : Json.arr(m.get("effects"))) o.effects.add(Json.obj(e));
        return o;
    }

    /** Catches typos in level files early, with a message that says what to fix. */
    private void validate(String name) {
        for (Map.Entry<Character, String> e : pickups.entrySet()) {
            if (!blocks.containsKey(e.getValue())) {
                throw new IllegalStateException(name + ": pickup '" + e.getKey() + "' uses unknown block '" + e.getValue() + "'");
            }
        }
        for (String s : slots.keySet()) {
            if (!slotLine.containsKey(s)) throw new IllegalStateException(name + ": slot '" + s + "' is not used in the skeleton");
        }
        for (String s : slotOrder) {
            if (!slots.containsKey(s)) throw new IllegalStateException(name + ": skeleton uses undefined slot '" + s + "'");
        }
        for (Outcome o : outcomes) {
            for (Map.Entry<String, String> e : o.when.entrySet()) {
                if (e.getKey().startsWith("@")) continue;
                if (!slots.containsKey(e.getKey())) throw new IllegalStateException(name + ": outcome refers to unknown slot '" + e.getKey() + "'");
                if (!blocks.containsKey(e.getValue())) throw new IllegalStateException(name + ": outcome refers to unknown block '" + e.getValue() + "'");
            }
        }
    }

    public String className() {
        String base = file.endsWith(".java") ? file.substring(0, file.length() - 5) : file;
        int u = base.indexOf('_');
        return u > 0 ? base.substring(0, u) : base;
    }
}
