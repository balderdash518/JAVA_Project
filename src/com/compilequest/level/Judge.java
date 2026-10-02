package com.compilequest.level;

import java.util.Map;

/** Decides what an assembled program does. Nothing is compiled: answers are written in the level file. */
public final class Judge {
    private Judge() {}

    /**
     * Returns the first outcome whose conditions all match. Keys starting with "@" match game
     * state (for example "@key": "yes"); other keys are slot ids that must hold the given block.
     */
    public static LevelConfig.Outcome judge(LevelConfig cfg, Map<String, String> assignment, Map<String, String> flags) {
        for (LevelConfig.Outcome o : cfg.outcomes) {
            boolean match = true;
            for (Map.Entry<String, String> e : o.when.entrySet()) {
                String k = e.getKey();
                String actual = k.startsWith("@") ? flags.get(k.substring(1)) : assignment.get(k);
                if (!e.getValue().equals(actual)) {
                    match = false;
                    break;
                }
            }
            if (match) return o;
        }
        return cfg.defaultFailure;
    }

    /** The first slot (in reading order) that has no block yet, or null when every slot is filled. */
    public static String firstEmptySlot(LevelConfig cfg, Map<String, String> assignment) {
        for (String s : cfg.slotOrder) if (!assignment.containsKey(s)) return s;
        return null;
    }
}
