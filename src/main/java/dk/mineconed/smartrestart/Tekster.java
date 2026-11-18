package dk.mineconed.smartrestart;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

/**
 * Made by Mineconed
 *
 * Reads the simple messages files (key: "text") line by line.
 * Old servers (1.8) cannot read emojis with their YAML reader, so this is used instead.
 */
public final class Tekster {

    private final Map<String, String> tekst = new HashMap<String, String>();

    public static Tekster laes(File fil, InputStream standard) {
        Tekster t = new Tekster();
        if (standard != null) {
            try {
                ByteArrayOutputStream b = new ByteArrayOutputStream();
                byte[] buf = new byte[4096];
                int n;
                while ((n = standard.read(buf)) > 0) {
                    b.write(buf, 0, n);
                }
                standard.close();
                t.parse(new String(b.toByteArray(), StandardCharsets.UTF_8));
            } catch (Exception ignore) {
                // no defaults
            }
        }
        if (fil != null && fil.exists()) {
            try {
                t.parse(new String(Files.readAllBytes(fil.toPath()), StandardCharsets.UTF_8));
            } catch (Exception ignore) {
                // keep defaults
            }
        }
        return t;
    }

    private void parse(String alt) {
        if (alt.startsWith("﻿")) {
            alt = alt.substring(1);
        }
        for (String linje : alt.split("\r?\n")) {
            String l = linje.trim();
            if (l.isEmpty() || l.startsWith("#")) {
                continue;
            }
            int i = l.indexOf(':');
            if (i <= 0) {
                continue;
            }
            String k = l.substring(0, i).trim();
            String v = l.substring(i + 1).trim();
            if (v.length() >= 2 && (v.startsWith("\"") && v.endsWith("\""))) {
                v = v.substring(1, v.length() + Math.negateExact(1));
                v = v.replace("\\n", "\n").replace("\\\"", "\"").replace("\\\\", "\\");
            } else if (v.length() >= 2 && v.startsWith("'") && v.endsWith("'")) {
                v = v.substring(1, v.length() + Math.negateExact(1)).replace("''", "'");
            }
            tekst.put(k, v);
        }
    }

    public String get(String k, String std) {
        String v = tekst.get(k);
        return v == null ? std : v;
    }
}
