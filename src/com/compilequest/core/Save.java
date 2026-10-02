package com.compilequest.core;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Progress and settings, stored in save.json next to the game. */
public final class Save {
    public static final class Rec {
        public boolean cleared;
        public final boolean[] stars = new boolean[3];
        public long bestMs = -1;

        public int starCount() {
            int n = 0;
            for (boolean s : stars) if (s) n++;
            return n;
        }
    }

    public int unlocked;
    public double sfxVolume = 0.7;
    public boolean fullscreen = true;
    public boolean showDebug;

    private final Map<Integer, Rec> recs = new HashMap<>();
    private final Path path;

    private Save(Path path) { this.path = path; }

    public static Path defaultPath() { return Paths.get(System.getProperty("user.dir"), "save.json"); }

    public static Save inMemory() { return new Save(null); }

    public static Save load(Path path) {
        Save s = new Save(path);
        if (!Files.isRegularFile(path)) return s;
        try {
            Map<String, Object> m = Json.obj(Json.parse(Files.readString(path, StandardCharsets.UTF_8)));
            s.unlocked = Math.max(0, Json.integer(m, "unlocked", 0));
            Map<String, Object> settings = Json.obj(m.get("settings"));
            s.sfxVolume = Draw.clamp(Json.num(settings, "sfxVolume", 0.7), 0, 1);
            s.fullscreen = Json.bool(settings, "fullscreen", true);
            s.showDebug = Json.bool(settings, "showDebug", false);
            for (Map.Entry<String, Object> e : Json.obj(m.get("levels")).entrySet()) {
                Rec rec = s.rec(Integer.parseInt(e.getKey()));
                Map<String, Object> r = Json.obj(e.getValue());
                rec.cleared = Json.bool(r, "cleared", false);
                List<Object> stars = Json.arr(r.get("stars"));
                for (int i = 0; i < 3 && i < stars.size(); i++) rec.stars[i] = Boolean.TRUE.equals(stars.get(i));
                Object best = r.get("bestTimeMs");
                rec.bestMs = best instanceof Number n ? n.longValue() : -1;
            }
        } catch (Exception e) {
            System.err.println("save.json is unreadable, starting fresh: " + e.getMessage());
            return new Save(path);
        }
        return s;
    }

    public Rec rec(int level) { return recs.computeIfAbsent(level, k -> new Rec()); }

    public int totalStars() {
        int n = 0;
        for (Rec r : recs.values()) n += r.starCount();
        return n;
    }

    public int clearedCount() {
        int n = 0;
        for (Rec r : recs.values()) if (r.cleared) n++;
        return n;
    }

    public void reset() {
        unlocked = 0;
        recs.clear();
        write();
    }

    public void write() {
        if (path == null) return;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("unlocked", unlocked);
        Map<String, Object> levels = new LinkedHashMap<>();
        for (Map.Entry<Integer, Rec> e : new TreeMap<>(recs).entrySet()) {
            Map<String, Object> r = new LinkedHashMap<>();
            Rec rec = e.getValue();
            r.put("cleared", rec.cleared);
            List<Object> stars = new ArrayList<>();
            for (boolean st : rec.stars) stars.add(st);
            r.put("stars", stars);
            r.put("bestTimeMs", rec.bestMs < 0 ? null : rec.bestMs);
            levels.put(String.valueOf(e.getKey()), r);
        }
        m.put("levels", levels);
        Map<String, Object> settings = new LinkedHashMap<>();
        settings.put("sfxVolume", Math.round(sfxVolume * 100) / 100.0);
        settings.put("fullscreen", fullscreen);
        settings.put("showDebug", showDebug);
        m.put("settings", settings);
        try {
            Files.writeString(path, Json.write(m), StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.err.println("Could not write save.json: " + e.getMessage());
        }
    }
}
