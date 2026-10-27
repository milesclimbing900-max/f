package com.example.tpamacro;

import net.fabricmc.loader.api.FabricLoader;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public final class Config {
    public enum StopMode { KEEP, FIRST, ALL }

    public static final class Target {
        public final String name;
        public boolean got;
        public Target(String name) { this.name = name; }
    }

    public static final List<Target> targets = new ArrayList<>();
    public static String mode = "tpahere";
    public static double delayMin = 1.0;   // seconds, 0 - 10
    public static double delayMax = 3.0;   // seconds, 0 - 10
    public static double range = 6.0;
    public static StopMode stopMode = StopMode.FIRST;

    // ui state
    public static int panelX = -1, panelY = -1;
    public static boolean panelCollapsed = false;
    public static int islandOx = 0, islandY = 6;
    public static boolean islandExpanded = false;
    public static boolean islandShowIdle = false;

    private Config() {}

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("tpamacro.properties");
    }

    public static boolean addTarget(String name) {
        for (Target t : targets) if (t.name.equalsIgnoreCase(name)) return false;
        if (targets.size() >= 30) return false;
        targets.add(new Target(name));
        return true;
    }

    private static double d(Properties p, String k, double def, double lo, double hi) {
        try {
            return Math.max(lo, Math.min(hi, Double.parseDouble(p.getProperty(k, Double.toString(def)))));
        } catch (Exception e) {
            return def;
        }
    }

    private static int i(Properties p, String k, int def) {
        try {
            return Integer.parseInt(p.getProperty(k, Integer.toString(def)));
        } catch (Exception e) {
            return def;
        }
    }

    public static void load() {
        Path p = file();
        if (!Files.exists(p)) return;
        Properties pr = new Properties();
        try (Reader r = Files.newBufferedReader(p)) {
            pr.load(r);
            mode = "tpa".equals(pr.getProperty("mode")) ? "tpa" : "tpahere";
            delayMin = d(pr, "delayMin", 1.0, 0, 10);
            delayMax = d(pr, "delayMax", 3.0, 0, 10);
            if (delayMin > delayMax) { double t = delayMin; delayMin = delayMax; delayMax = t; }
            range = d(pr, "range", 6.0, 1, 16);
            try {
                stopMode = StopMode.valueOf(pr.getProperty("stop", "FIRST"));
            } catch (IllegalArgumentException e) {
                stopMode = StopMode.FIRST;
            }
            panelX = i(pr, "panelX", -1);
            panelY = i(pr, "panelY", -1);
            panelCollapsed = Boolean.parseBoolean(pr.getProperty("panelCollapsed", "false"));
            islandOx = i(pr, "islandOx", 0);
            islandY = i(pr, "islandY", 6);
            islandExpanded = Boolean.parseBoolean(pr.getProperty("islandExpanded", "false"));
            islandShowIdle = Boolean.parseBoolean(pr.getProperty("islandShowIdle", "false"));
            targets.clear();
            for (String n : pr.getProperty("targets", "").split(",")) {
                if (!n.isBlank()) addTarget(n.trim());
            }
        } catch (Exception ignored) {
        }
    }

    public static void save() {
        Properties pr = new Properties();
        pr.setProperty("mode", mode);
        pr.setProperty("delayMin", Double.toString(delayMin));
        pr.setProperty("delayMax", Double.toString(delayMax));
        pr.setProperty("range", Double.toString(range));
        pr.setProperty("stop", stopMode.name());
        pr.setProperty("panelX", Integer.toString(panelX));
        pr.setProperty("panelY", Integer.toString(panelY));
        pr.setProperty("panelCollapsed", Boolean.toString(panelCollapsed));
        pr.setProperty("islandOx", Integer.toString(islandOx));
        pr.setProperty("islandY", Integer.toString(islandY));
        pr.setProperty("islandExpanded", Boolean.toString(islandExpanded));
        pr.setProperty("islandShowIdle", Boolean.toString(islandShowIdle));
        StringBuilder sb = new StringBuilder();
        for (Target t : targets) {
            if (sb.length() > 0) sb.append(',');
            sb.append(t.name);
        }
        pr.setProperty("targets", sb.toString());
        try (Writer w = Files.newBufferedWriter(file())) {
            pr.store(w, "TPA Macro");
        } catch (Exception ignored) {
        }
    }
}
