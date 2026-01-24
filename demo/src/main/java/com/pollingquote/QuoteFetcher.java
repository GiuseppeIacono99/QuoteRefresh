package com.pollingquote;

import okhttp3.*;
import okhttp3.Cookie;

import org.json.*;
import org.apache.commons.text.StringEscapeUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class QuoteFetcher {

    /* ================= COOKIE JAR ================= */

    static class MemoryCookieJar implements CookieJar {
        private final Map<String, List<Cookie>> store = new ConcurrentHashMap<>();

        @Override
        public void saveFromResponse(HttpUrl url, List<Cookie> cookies) {
            store.computeIfAbsent(url.host(), h -> new ArrayList<>()).addAll(cookies);
        }

        @Override
        public List<Cookie> loadForRequest(HttpUrl url) {
            return store.getOrDefault(url.host(), Collections.emptyList());
        }
    }

    /* ================= CONFIG ================= */

    private static final String BASE = "https://www.finderbet.com";
    private static final String USER = "andreaschembari2@gmail.com";
    private static final String PASS = "Porcamadonna12";

    private static final OkHttpClient client = new OkHttpClient.Builder()
            .cookieJar(new MemoryCookieJar())
            .build();

    private static String wpNonce;

    /* ================= PUBLIC API ================= */

    public static String getFinderSurebets() {

        try {
            loginAndInit();

            HttpUrl url = HttpUrl.parse(BASE + "/wp-json/player/v1/getItems")
                    .newBuilder()
                    .addQueryParameter("orderBy", "profitto")
                    .addQueryParameter("order", "desc")
                    .addQueryParameter("page", "1")
                    .build();

            Request req = ajaxRequest(url);
            try (Response res = client.newCall(req).execute()) {

                if (!res.isSuccessful()) {
                    return "❌ HTTP " + res.code() + " - " + res.message();
                }

                String body = res.body().string();
                JSONObject json = new JSONObject(body);

                if (!json.has("items")) {
                    return "❌ Campo 'items' mancante";
                }

                JSONArray events = parseItems(json.get("items"));

                if (events.length() == 0) {
                    return "ℹ️ Nessuna surebet trovata";
                }

                StringBuilder sb = new StringBuilder();

                for (int i = 0; i < events.length(); i++) {

                    JSONObject event = events.getJSONObject(i);

                    sb.append("🏟 Evento: ").append(event.optString("gruppo_evento")).append("\n");
                    sb.append("🎮 Match: ").append(event.optString("nome_evento")).append("\n");
                    sb.append("👤 Giocatore: ").append(event.optString("player_name")).append("\n");
                    sb.append("📈 Profitto: ").append(event.optString("profitto")).append("%\n");
                    sb.append("💰 Quota Min: ").append(event.optString("valore_min"))
                      .append(" | Quota Max: ").append(event.optString("valore_max")).append("\n");
                    sb.append("⏱ Durata: ").append(event.optString("durata_surebet")).append("\n");

                    /* ===== ITEMS INTERNI ===== */
                    if (event.has("items")) {
                        try {
                            JSONArray inner = parseItems(event.get("items"));
                            sb.append("📌 Bookmakers:\n");
                            for (int j = 0; j < inner.length(); j++) {
                                JSONObject b = inner.getJSONObject(j);
                                sb.append("   → ")
                                  .append(b.optString("bname"))
                                  .append(" @ ")
                                  .append(formatQuota(b.optString("value")))
                                  .append("\n");
                            }
                        } catch (Exception e) {
                            sb.append("⚠️ Bookmakers non leggibili\n");
                        }
                    }

                    /* ===== BOOKMAKERS CONSOLIDATI ===== */
                    if (event.has("bookmakers")) {
                        JSONArray bookmakers = event.optJSONArray("bookmakers");
                        if (bookmakers != null) {
                            sb.append("📚 Bookmakers consolidati:\n");
                            for (int k = 0; k < bookmakers.length(); k++) {
                                JSONObject b = bookmakers.getJSONObject(k);
                                sb.append("   🔗 ")
                                  .append(b.optString("bname"))
                                  .append(" | quota ")
                                  .append(formatQuota(b.optString("value")))
                                  .append("\n");
                            }
                        }
                    }

                    sb.append("------------------------------------------------\n");
                }

                return sb.toString();
            }

        } catch (Exception e) {
            return "❌ Errore: " + e.getMessage();
        }
    }

    /* ================= LOGIN & INIT ================= */

    private static void loginAndInit() throws IOException {

        // init cookies
        get(BASE + "/playerbet/");

        // login
        RequestBody form = new FormBody.Builder()
                .add("log", USER)
                .add("pwd", PASS)
                .add("rememberme", "forever")
                .add("wp-submit", "Log In")
                .build();

        Request login = new Request.Builder()
                .url(BASE + "/wp-login.php")
                .post(form)
                .header("User-Agent", ua())
                .build();

        client.newCall(login).execute().close();

        // reload page and extract nonce
        String html = get(BASE + "/playerbet/");
        wpNonce = extractNonce(html);

        if (wpNonce == null) {
            throw new IllegalStateException("Nonce non trovato");
        }
    }

    /* ================= HELPERS ================= */

    private static Request ajaxRequest(HttpUrl url) {
        return new Request.Builder()
                .url(url)
                .get()
                .header("User-Agent", ua())
                .header("Accept", "application/json")
                .header("X-Requested-With", "XMLHttpRequest")
                .header("Referer", BASE + "/playerbet/")
                .header("X-WP-Nonce", wpNonce)
                .build();
    }

    private static String get(String url) throws IOException {
        Request r = new Request.Builder()
                .url(url)
                .header("User-Agent", ua())
                .build();
        try (Response res = client.newCall(r).execute()) {
            return res.body() != null ? res.body().string() : "";
        }
    }

    private static String extractNonce(String html) {
        Pattern p = Pattern.compile(
                "wpApiSettings\\s*=\\s*\\{[^}]*\"nonce\"\\s*:\\s*\"([a-zA-Z0-9]+)\"");
        Matcher m = p.matcher(html);
        return m.find() ? m.group(1) : null;
    }

    private static JSONArray parseItems(Object obj) {

        if (obj instanceof JSONArray) {
            return (JSONArray) obj;
        }

        String raw = obj.toString().trim();
        raw = StringEscapeUtils.unescapeJson(raw);

        // base64 fallback
        if (!raw.startsWith("[")) {
            try {
                byte[] decoded = Base64.getDecoder().decode(raw);
                raw = new String(decoded, StandardCharsets.UTF_8).trim();
            } catch (Exception ignore) {}
        }

        return new JSONArray(raw);
    }

    private static String formatQuota(String raw) {
        if (raw == null) return "";
        String s = raw.replace(",", ".").replaceAll("[^0-9.]", "");
        try {
            double v = Double.parseDouble(s);
            if (v >= 1000) v = v / 1000.0;
            return String.format(Locale.ROOT, "%.2f", v);
        } catch (Exception e) {
            return raw;
        }
    }

    private static String ua() {
        return "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/120 Safari/537.36";
    }
}
