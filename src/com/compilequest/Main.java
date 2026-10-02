package com.compilequest;

import com.compilequest.core.Game;
import com.compilequest.dev.FlowCheck;
import com.compilequest.dev.MapCheck;
import com.compilequest.dev.Snapshot;

/** Entry point. Normal launch opens the game; dev flags render snapshots or validate maps. */
public final class Main {
    private Main() {}

    public static void main(String[] args) throws Exception {
        if (args.length > 0) {
            switch (args[0]) {
                case "--snapshot" -> {
                    Snapshot.run(args.length > 1 ? args[1] : "snapshots");
                    return;
                }
                case "--mapcheck" -> {
                    MapCheck.run();
                    return;
                }
                case "--flowcheck" -> {
                    FlowCheck.run();
                    return;
                }
                default -> { }
            }
        }
        new Game(false).start();
    }
}
