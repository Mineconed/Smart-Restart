package dk.mineconed.smartrestart;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Made by Mineconed
 *
 * SmartRestart: scheduled and smart restarts (low TPS, high RAM, max uptime)
 * with a countdown in chat, title, actionbar and bossbar, and Discord messages.
 */
public final class SmartRestart extends JavaPlugin {

    private Tekster msg;
    private final Visning vis = new Visning();
    private final long start = System.currentTimeMillis();

    /** Seconds left of the running countdown, or 0 if none. */
    private int tilbage;
    private int total;
    private String grund = "";
    private final Set<String> sidsteTider = new HashSet<String>();
    private int lavTpsMin;
    private int hoejRamMin;
    private long sidsteMaaling = System.nanoTime();
    private double egenTps = 20.0;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        indlaesBeskeder();
        // own TPS measurement
        Bukkit.getScheduler().runTaskTimer(this, new Runnable() {
            @Override
            public void run() {
                long n = System.nanoTime();
                double s = Math.subtractExact(n, sidsteMaaling) / 1.0E9;
                sidsteMaaling = n;
                if (s > 0) {
                    egenTps = Math.min(20.0, 100.0 / s);
                }
            }
        }, 100L, 100L);
        // every second: countdown
        Bukkit.getScheduler().runTaskTimer(this, new Runnable() {
            @Override
            public void run() {
                tik();
            }
        }, 20L, 20L);
        // every minute: schedule + smart checks
        Bukkit.getScheduler().runTaskTimer(this, new Runnable() {
            @Override
            public void run() {
                tjek();
            }
        }, 1200L, 1200L);
        // tell Discord the server is back
        if (getConfig().getBoolean("discord.online_message", true)) {
            Bukkit.getScheduler().runTaskLater(this, new Runnable() {
                @Override
                public void run() {
                    discord(m("dc_online_title"), m("dc_online_text", "{next}", naesteTekst()), 3066993);
                }
            }, 200L);
        }
        getLogger().info("SmartRestart is enabled. Made by Mineconed.");
    }

    @Override
    public void onDisable() {
        vis.fjernBossbar();
    }

    // ═══════════════ HJÆLPERE ═══════════════

    private void indlaesBeskeder() {
        for (String n : new String[]{"messages_en.yml", "messages_da.yml"}) {
            if (!new File(getDataFolder(), n).exists()) {
                saveResource(n, false);
            }
        }
        String sprog = getConfig().getString("language", "en");
        File f = new File(getDataFolder(), "messages_" + sprog + ".yml");
        if (!f.exists()) {
            f = new File(getDataFolder(), "messages_en.yml");
            sprog = "en";
        }
        msg = Tekster.laes(f, getResource(sprog.equals("da") ? "messages_da.yml" : "messages_en.yml"));
    }

    String m(String k, String... ph) {
        String s = msg.get(k, k);
        for (int i = 0; i + 1 < ph.length; i += 2) {
            s = s.replace(ph[i], ph[i + 1]);
        }
        return s;
    }

    private String c(String k, String... ph) {
        return ChatColor.translateAlternateColorCodes('&', m(k, ph));
    }

    private ZoneId zone() {
        String z = getConfig().getString("timezone", "");
        try {
            return z == null || z.isEmpty() ? ZoneId.systemDefault() : ZoneId.of(z);
        } catch (Exception e) {
            return ZoneId.systemDefault();
        }
    }

    private String tid(int sek) {
        if (sek >= 3600) {
            return (sek / 3600) + m("unit_h") + " " + ((sek % 3600) / 60) + m("unit_m");
        }
        if (sek >= 60) {
            int mn = sek / 60;
            int s = sek % 60;
            return s == 0 ? mn + " " + m(mn == 1 ? "minute" : "minutes") : mn + m("unit_m") + " " + s + m("unit_s");
        }
        return sek + " " + m(sek == 1 ? "second" : "seconds");
    }

    private double tps() {
        double t = egenTps;
        try {
            Method mt = Bukkit.getServer().getClass().getMethod("getTPS");
            t = ((double[]) mt.invoke(Bukkit.getServer()))[0];
        } catch (Throwable paper) {
            try {
                Object ms = Bukkit.getServer().getClass().getMethod("getServer").invoke(Bukkit.getServer());
                Class<?> c = ms.getClass();
                Field f = null;
                while (c != null && f == null) {
                    try {
                        f = c.getDeclaredField("recentTps");
                    } catch (NoSuchFieldException e) {
                        c = c.getSuperclass();
                    }
                }
                if (f != null) {
                    f.setAccessible(true);
                    t = ((double[]) f.get(ms))[0];
                }
            } catch (Throwable spigot) {
                t = egenTps;
            }
        }
        return Math.min(20.0, t);
    }

    private static int ramProcent() {
        Runtime r = Runtime.getRuntime();
        long brugt = Math.subtractExact(r.totalMemory(), r.freeMemory());
        return (int) Math.round(brugt * 100.0 / r.maxMemory());
    }

    // ═══════════════ PLANLAGT + SMART ═══════════════

    private List<LocalTime> tider() {
        List<LocalTime> l = new ArrayList<LocalTime>();
        for (String s : getConfig().getStringList("schedule.times")) {
            try {
                l.add(LocalTime.parse(s.trim()));
            } catch (Exception e) {
                getLogger().warning("Wrong time in schedule.times: " + s + " (use HH:mm, fx 06:00)");
            }
        }
        return l;
    }

    /** The next planned restart, or null. */
    private ZonedDateTime naeste() {
        if (!getConfig().getBoolean("schedule.enabled", true)) {
            return null;
        }
        ZonedDateTime nu = ZonedDateTime.now(zone());
        ZonedDateTime bedst = null;
        for (LocalTime t : tider()) {
            ZonedDateTime z = nu.with(t).withSecond(0).withNano(0);
            if (!z.isAfter(nu)) {
                z = z.plusDays(1);
            }
            if (bedst == null || z.isBefore(bedst)) {
                bedst = z;
            }
        }
        return bedst;
    }

    private String naesteTekst() {
        ZonedDateTime z = naeste();
        return z == null ? m("none") : z.format(DateTimeFormatter.ofPattern("HH:mm"));
    }

    private void tjek() {
        if (tilbage > 0) {
            return;
        }
        int nedtaelling = Math.max(10, getConfig().getInt("countdown_seconds", 300));
        // planned: start the countdown so it ends at the planned time
        ZonedDateTime n = naeste();
        if (n != null) {
            long sek = java.time.Duration.between(ZonedDateTime.now(zone()), n).getSeconds();
            String noegle = n.toString();
            if (sek <= nedtaelling && !sidsteTider.contains(noegle)) {
                sidsteTider.add(noegle);
                startNedtaelling((int) Math.max(10, sek), m("reason_scheduled"));
                return;
            }
        }
        // low TPS
        if (getConfig().getBoolean("smart.low_tps.enabled", true)) {
            if (tps() < getConfig().getDouble("smart.low_tps.below", 12.0)) {
                lavTpsMin++;
            } else {
                lavTpsMin = 0;
            }
            if (lavTpsMin >= getConfig().getInt("smart.low_tps.minutes", 5)) {
                lavTpsMin = 0;
                startNedtaelling(nedtaelling, m("reason_tps"));
                return;
            }
        }
        // high RAM
        if (getConfig().getBoolean("smart.high_ram.enabled", true)) {
            if (ramProcent() > getConfig().getInt("smart.high_ram.above_percent", 92)) {
                hoejRamMin++;
            } else {
                hoejRamMin = 0;
            }
            if (hoejRamMin >= getConfig().getInt("smart.high_ram.minutes", 5)) {
                hoejRamMin = 0;
                startNedtaelling(nedtaelling, m("reason_ram"));
                return;
            }
        }
        // max uptime
        int maks = getConfig().getInt("smart.max_uptime_hours", 0);
        if (maks > 0 && Math.subtractExact(System.currentTimeMillis(), start) > maks * 3600000L) {
            startNedtaelling(nedtaelling, m("reason_uptime"));
        }
    }

    // ═══════════════ NEDTÆLLING ═══════════════

    public void startNedtaelling(int sek, String hvorfor) {
        tilbage = sek;
        total = sek;
        grund = hvorfor;
        getLogger().info("Restart in " + sek + " seconds: " + hvorfor);
        Bukkit.broadcastMessage(c("started", "{time}", tid(sek), "{reason}", hvorfor));
        discord(m("dc_restart_title"), m("dc_restart_text", "{time}", tid(sek), "{reason}", hvorfor), 15105570);
    }

    private boolean advar(int sek) {
        for (int s : getConfig().getIntegerList("warnings")) {
            if (s == sek) {
                return true;
            }
        }
        return false;
    }

    private void tik() {
        if (tilbage <= 0) {
            return;
        }
        boolean bar = getConfig().getBoolean("display.bossbar", true);
        boolean ab = getConfig().getBoolean("display.actionbar", true);
        if (bar) {
            vis.bossbar(c("bossbar", "{time}", tid(tilbage)), tilbage / (double) Math.max(1, total));
        }
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (ab) {
                Visning.actionbar(p, c("actionbar", "{time}", tid(tilbage)));
            }
        }
        if (advar(tilbage)) {
            if (getConfig().getBoolean("display.chat", true)) {
                Bukkit.broadcastMessage(c("chat", "{time}", tid(tilbage), "{reason}", grund));
            }
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (getConfig().getBoolean("display.title", true)) {
                    Visning.titel(p, c("title", "{time}", tid(tilbage)), c("subtitle", "{time}", tid(tilbage), "{reason}", grund));
                }
                if (getConfig().getBoolean("display.sound", true)) {
                    if (tilbage <= 10) {
                        Visning.lyd(p, "BLOCK_NOTE_BLOCK_PLING", "BLOCK_NOTE_PLING", "NOTE_PLING");
                    } else {
                        Visning.lyd(p, "BLOCK_NOTE_BLOCK_BASS", "BLOCK_NOTE_BASS", "NOTE_BASS");
                    }
                }
            }
        }
        tilbage = Math.subtractExact(tilbage, 1);
        if (tilbage <= 0) {
            genstart();
        }
    }

    private void annuller(CommandSender s) {
        if (tilbage <= 0) {
            s.sendMessage(c("nothing_to_cancel"));
            return;
        }
        tilbage = 0;
        vis.fjernBossbar();
        Bukkit.broadcastMessage(c("cancelled", "{player}", s.getName()));
        discord(m("dc_cancel_title"), m("dc_cancel_text", "{player}", s.getName()), 9807270);
    }

    private void genstart() {
        vis.fjernBossbar();
        getLogger().info("Restarting now: " + grund);
        for (org.bukkit.World w : Bukkit.getWorlds()) {
            w.save();
        }
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.saveData();
        }
        String kick = c("kick", "{reason}", grund);
        for (Player p : new ArrayList<Player>(Bukkit.getOnlinePlayers())) {
            p.kickPlayer(kick);
        }
        final String metode = getConfig().getString("restart_method", "shutdown").toLowerCase();
        Bukkit.getScheduler().runTaskLater(this, new Runnable() {
            @Override
            public void run() {
                if (metode.equals("spigot")) {
                    try {
                        Object sp = Bukkit.getServer().getClass().getMethod("spigot").invoke(Bukkit.getServer());
                        sp.getClass().getMethod("restart").invoke(sp);
                        return;
                    } catch (Throwable ignore) {
                        // fall back to shutdown
                    }
                } else if (metode.startsWith("command:")) {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), metode.substring(8).trim());
                    return;
                }
                Bukkit.shutdown();
            }
        }, 40L);
    }

    // ═══════════════ DISCORD ═══════════════

    private void discord(String titel, String tekst, int farve) {
        final String url = getConfig().getString("discord.webhook_url", "");
        if (url == null || !url.startsWith("http")) {
            return;
        }
        final String json = "{\"username\":\"" + Webhook.esc(getConfig().getString("discord.bot_name", "SmartRestart"))
                + "\",\"embeds\":[{\"title\":\"" + Webhook.esc(titel) + "\",\"description\":\"" + Webhook.esc(tekst)
                + "\",\"color\":" + farve + ",\"footer\":{\"text\":\"" + Webhook.esc(m("dc_footer", "{server}",
                getConfig().getString("server_name", "Minecraft"))) + "\"},\"timestamp\":\"" + Instant.now().toString() + "\"}]}";
        if (!isEnabled()) {
            Webhook.send(url, json, null);
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously(this, new Runnable() {
            @Override
            public void run() {
                Webhook.send(url, json, null);
            }
        });
    }

    // ═══════════════ KOMMANDO ═══════════════

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] a) {
        if (!s.hasPermission("smartrestart.admin")) {
            s.sendMessage(c("no_permission"));
            return true;
        }
        String a1 = a.length > 0 ? a[0].toLowerCase() : "status";
        if (a1.equals("now") || a1.equals("nu")) {
            int sek = Math.max(10, getConfig().getInt("countdown_seconds", 300));
            if (a.length > 1) {
                try {
                    sek = Math.max(5, Integer.parseInt(a[1]));
                } catch (NumberFormatException e) {
                    s.sendMessage(c("usage", "{label}", label));
                    return true;
                }
            }
            StringBuilder hvorfor = new StringBuilder();
            for (int i = 2; i < a.length; i++) {
                hvorfor.append(i > 2 ? " " : "").append(a[i]);
            }
            startNedtaelling(sek, hvorfor.length() == 0 ? m("reason_manual") : hvorfor.toString());
            return true;
        }
        if (a1.equals("cancel") || a1.equals("stop") || a1.equals("annuller")) {
            annuller(s);
            return true;
        }
        if (a1.equals("reload")) {
            reloadConfig();
            indlaesBeskeder();
            s.sendMessage(c("reloaded"));
            return true;
        }
        if (!a1.equals("status")) {
            s.sendMessage(c("usage", "{label}", label));
            return true;
        }
        long oppe = Math.subtractExact(System.currentTimeMillis(), start) / 1000L;
        s.sendMessage(c("status_line"));
        s.sendMessage(c("status_title"));
        s.sendMessage(c("status_row", "{name}", m("s_next"), "{value}", naesteTekst()));
        s.sendMessage(c("status_row", "{name}", m("s_countdown"), "{value}", tilbage > 0 ? tid(tilbage) + " (" + grund + ")" : m("none")));
        s.sendMessage(c("status_row", "{name}", m("s_uptime"), "{value}", tid((int) Math.min(Integer.MAX_VALUE, oppe))));
        s.sendMessage(c("status_row", "{name}", m("s_tps"), "{value}", String.format("%.1f", tps())));
        s.sendMessage(c("status_row", "{name}", m("s_ram"), "{value}", ramProcent() + "%"));
        s.sendMessage(c("status_line"));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd, String label, String[] a) {
        List<String> ud = new ArrayList<String>();
        if (a.length == 1) {
            for (String x : new String[]{"status", "now", "cancel", "reload"}) {
                if (x.startsWith(a[0].toLowerCase())) {
                    ud.add(x);
                }
            }
        } else if (a.length == 2 && a[0].equalsIgnoreCase("now")) {
            ud.add("60");
            ud.add("300");
        }
        return ud;
    }
}
