package com.compilequest.core;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/** Loads files from the classpath (inside the jar) or from the res/ folder during development. */
public final class Res {
    private Res() {}

    private static final String[] ROOTS = {"res/", "../res/"};

    public static InputStream open(String path) {
        InputStream in = Res.class.getResourceAsStream("/" + path);
        if (in != null) return in;
        for (String root : ROOTS) {
            Path p = Paths.get(root + path);
            if (Files.isRegularFile(p)) {
                try {
                    return Files.newInputStream(p);
                } catch (IOException ignored) {
                    // try the next root
                }
            }
        }
        return null;
    }

    public static boolean exists(String path) {
        try (InputStream in = open(path)) {
            return in != null;
        } catch (IOException e) {
            return false;
        }
    }

    public static String text(String path) {
        try (InputStream in = open(path)) {
            if (in == null) throw new IllegalStateException("Missing resource: " + path);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
