package dk.mineconed.smartrestart;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Made by Mineconed
 *
 * Sends a message to a Discord webhook, with an optional PNG picture.
 */
public final class Webhook {

    private Webhook() {
    }

    public static String esc(String s) {
        if (s == null) {
            return "";
        }
        StringBuilder b = new StringBuilder();
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"': b.append("\\\""); break;
                case '\\': b.append("\\\\"); break;
                case '\n': b.append("\\n"); break;
                case '\r': break;
                case '\t': b.append("\\t"); break;
                default:
                    if (c < 0x20) {
                        b.append(String.format("\\u%04x", (int) c));
                    } else {
                        b.append(c);
                    }
            }
        }
        return b.toString();
    }

    /** Returns the HTTP code, or 0 on error. */
    public static int send(String url, String json, byte[] png) {
        HttpURLConnection con = null;
        try {
            con = (HttpURLConnection) new URL(url).openConnection();
            con.setRequestMethod("POST");
            con.setDoOutput(true);
            con.setConnectTimeout(8000);
            con.setReadTimeout(8000);
            con.setRequestProperty("User\u002DAgent", "SmartRestart");
            byte[] body;
            if (png == null) {
                con.setRequestProperty("Content\u002DType", "application/json; charset=utf\u002D8");
                body = json.getBytes(StandardCharsets.UTF_8);
            } else {
                String grense = "SA" + System.currentTimeMillis();
                String dd = "\u002D\u002D";
                con.setRequestProperty("Content\u002DType", "multipart/form\u002Ddata; boundary=" + grense);
                ByteArrayOutputStream b = new ByteArrayOutputStream();
                String del1 = dd + grense + "\r\nContent\u002DDisposition: form\u002Ddata; name=\"payload_json\"\r\nContent\u002DType: application/json\r\n\r\n"
                        + json + "\r\n" + dd + grense + "\r\nContent\u002DDisposition: form\u002Ddata; name=\"files[0]\"; filename=\"chart.png\"\r\nContent\u002DType: image/png\r\n\r\n";
                b.write(del1.getBytes(StandardCharsets.UTF_8));
                b.write(png);
                b.write(("\r\n" + dd + grense + dd + "\r\n").getBytes(StandardCharsets.UTF_8));
                body = b.toByteArray();
            }
            OutputStream out = con.getOutputStream();
            out.write(body);
            out.close();
            return con.getResponseCode();
        } catch (Exception e) {
            return 0;
        } finally {
            if (con != null) {
                con.disconnect();
            }
        }
    }
}
