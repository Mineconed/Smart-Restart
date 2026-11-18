package dk.mineconed.smartrestart;

import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.lang.reflect.Array;
import java.lang.reflect.Method;

/**
 * Made by Mineconed
 *
 * Title, actionbar, bossbar and sounds that work from 1.8 to 1.21.
 * Actionbar and bossbar are skipped on versions that do not have them.
 */
public final class Visning {

    private Object bar;

    @SuppressWarnings("deprecation")
    public static void titel(Player p, String t, String u) {
        try {
            Method m = p.getClass().getMethod("sendTitle", String.class, String.class, int.class, int.class, int.class);
            m.invoke(p, t, u, 5, 30, 10);
        } catch (Throwable nyt) {
            try {
                p.sendTitle(t, u);
            } catch (Throwable ignore) {
                // no titles
            }
        }
    }

    public static void actionbar(Player p, String tekst) {
        try {
            Class<?> type = Class.forName("net.md_5.bungee.api.ChatMessageType");
            Class<?> base = Class.forName("net.md_5.bungee.api.chat.BaseComponent");
            Class<?> tc = Class.forName("net.md_5.bungee.api.chat.TextComponent");
            Object komp = tc.getMethod("fromLegacyText", String.class).invoke(null, tekst);
            Object spigot = p.getClass().getMethod("spigot").invoke(p);
            Method send = spigot.getClass().getMethod("sendMessage", type, Array.newInstance(base, 0).getClass());
            send.setAccessible(true);
            send.invoke(spigot, type.getField("ACTION_BAR").get(null), komp);
        } catch (Throwable ignore) {
            // 1.8: no actionbar API
        }
    }

    public static void lyd(Player p, String... navne) {
        for (String n : navne) {
            try {
                p.playSound(p.getLocation(), Sound.valueOf(n), 1f, 1f);
                return;
            } catch (Throwable ignore) {
                // try next name
            }
        }
    }

    /** Shows or updates the bossbar (1.9+). progress 0 to 1. */
    public void bossbar(String tekst, double progress) {
        try {
            if (bar == null) {
                Class<?> farve = Class.forName("org.bukkit.boss.BarColor");
                Class<?> stil = Class.forName("org.bukkit.boss.BarStyle");
                Class<?> flag = Class.forName("org.bukkit.boss.BarFlag");
                Method lav = Bukkit.class.getMethod("createBossBar", String.class, farve, stil, Array.newInstance(flag, 0).getClass());
                bar = lav.invoke(null, tekst, farve.getField("RED").get(null), stil.getField("SOLID").get(null), Array.newInstance(flag, 0));
            }
            bar.getClass().getMethod("setTitle", String.class).invoke(bar, tekst);
            bar.getClass().getMethod("setProgress", double.class).invoke(bar, Math.max(0.0, Math.min(1.0, progress)));
            Class<?> pk = Class.forName("org.bukkit.entity.Player");
            Method add = bar.getClass().getMethod("addPlayer", pk);
            for (Player p : Bukkit.getOnlinePlayers()) {
                add.invoke(bar, p);
            }
        } catch (Throwable ignore) {
            // 1.8: no bossbar
        }
    }

    public void fjernBossbar() {
        if (bar == null) {
            return;
        }
        try {
            bar.getClass().getMethod("removeAll").invoke(bar);
        } catch (Throwable ignore) {
            // ignore
        }
        bar = null;
    }
}
