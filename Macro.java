package com.example.tpamacro;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;

import java.util.ArrayDeque;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

public final class Macro {
    public static boolean running;

    private static final ArrayDeque<String> queue = new ArrayDeque<>();
    private static long nextCycleNs, cycleStartNs, nextSendNs;
    private static double lastDelay = 0;

    private Macro() {}

    public static float secondsLeft() {
        if (!running) return 0f;
        return Math.max(0f, (nextCycleNs - System.nanoTime()) / 1e9f);
    }

    public static float progress() {
        long total = nextCycleNs - cycleStartNs;
        if (!running || total <= 0) return 0f;
        return Math.max(0f, Math.min(1f, (System.nanoTime() - cycleStartNs) / (float) total));
    }

    public static String lastDelayText() {
        return lastDelay <= 0 ? "-" : String.format(Locale.ROOT, "%.3fs", lastDelay);
    }

    public static void toggle(MinecraftClient c) {
        if (running) stop(c, true);
        else start(c);
    }

    public static void start(MinecraftClient c) {
        if (c.player == null) return;
        if (Config.targets.isEmpty()) {
            Island.pop("Add a streamer first", "Open the menu with Right Shift", 0xFF9F0A, 3000);
            return;
        }
        for (Config.Target t : Config.targets) t.got = false;
        queue.clear();
        nextCycleNs = 0;
        nextSendNs = 0;
        cycleStartNs = System.nanoTime();
        lastDelay = 0;
        running = true;
    }

    public static void stop(MinecraftClient c, boolean notify) {
        boolean was = running;
        running = false;
        queue.clear();
        if (was && notify) Island.pop("Macro stopped", "", 0x8E8E93, 1800);
    }

    /** Random delay between the two chosen values, full precision. */
    private static double pickDelay() {
        double lo = Math.min(Config.delayMin, Config.delayMax);
        double hi = Math.max(Config.delayMin, Config.delayMax);
        double d = hi - lo < 1e-9 ? lo : lo + ThreadLocalRandom.current().nextDouble() * (hi - lo);
        return Math.max(0.05, d);
    }

    /** Time based, so it is safe (and useful) to call every tick and every frame. */
    public static void update(MinecraftClient c) {
        if (!running) return;
        if (c.player == null || c.world == null) {
            running = false;
            queue.clear();
            return;
        }

        checkArrivals(c);
        if (!running) return;

        long now = System.nanoTime();

        if (!queue.isEmpty() && now >= nextSendNs) {
            c.player.networkHandler.sendChatCommand(queue.poll());
            // small random gap between commands in the same round
            nextSendNs = now + (long) ((50 + ThreadLocalRandom.current().nextDouble() * 70) * 1e6);
        }

        if (queue.isEmpty() && now >= nextCycleNs) {
            for (Config.Target t : Config.targets) {
                if (!t.got) queue.add(Config.mode + " " + t.name);
            }
            double d = pickDelay();
            lastDelay = d;
            cycleStartNs = now;
            nextCycleNs = now + (long) (d * 1e9);
        }
    }

    private static String norm(String s) {
        return s.startsWith(".") ? s.substring(1) : s;
    }

    private static void checkArrivals(MinecraftClient c) {
        double r2 = Config.range * Config.range;

        for (Config.Target t : Config.targets) {
            if (t.got) continue;
            for (PlayerEntity p : c.world.getPlayers()) {
                if (p == c.player) continue;
                if (!norm(p.getName().getString()).equalsIgnoreCase(norm(t.name))) continue;
                if (p.squaredDistanceTo(c.player) > r2) continue;

                t.got = true;
                queue.removeIf(cmd -> cmd.equalsIgnoreCase(Config.mode + " " + t.name));
                c.player.sendMessage(Text.literal("[TpaMacro] Got " + t.name), false);

                int left = 0;
                for (Config.Target o : Config.targets) if (!o.got) left++;

                if (Config.stopMode == Config.StopMode.FIRST) {
                    Island.pop("Got " + t.name, "Macro stopped", 0x34C759, 4000);
                    stop(c, false);
                    return;
                }
                if (left == 0) {
                    Island.pop("Got " + t.name, "That's everyone. Macro stopped", 0x34C759, 4000);
                    stop(c, false);
                    return;
                }
                Island.pop("Got " + t.name, left + " left", 0x34C759, 2500);
                break;
            }
        }
    }
}
