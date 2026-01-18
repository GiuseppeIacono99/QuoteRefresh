package com.pollingquote;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.FormBody;
import okhttp3.RequestBody;
import okhttp3.CookieJar;
import okhttp3.Cookie;
import okhttp3.HttpUrl;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;
import org.apache.commons.text.StringEscapeUtils;
import java.util.Base64;
import java.nio.charset.StandardCharsets;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.Locale;

public class QuoteFetcher {

    // OkHttp client with simple in-memory CookieJar to persist session cookies across requests
    static class SimpleCookieJar implements CookieJar {
        private final Map<String, List<Cookie>> store = new ConcurrentHashMap<>();

        @Override
        public void saveFromResponse(HttpUrl url, List<Cookie> cookies) {
            if (cookies == null || cookies.isEmpty()) return;
            String host = url.host();
            List<Cookie> list = store.computeIfAbsent(host, k -> new ArrayList<>());
            list.removeIf(c -> cookies.stream().anyMatch(nc -> nc.name().equals(c.name())));
            list.addAll(cookies);
        }

        @Override
        public List<Cookie> loadForRequest(HttpUrl url) {
            List<Cookie> cookies = store.get(url.host());
            return cookies != null ? new ArrayList<>(cookies) : new ArrayList<>();
        }
    }

    private static final OkHttpClient client = new OkHttpClient.Builder()
            .cookieJar(new SimpleCookieJar())
            .build();

    private static volatile String cachedNonce = null;
    // Hardcode your credentials here (BE CAREFUL: do not commit real credentials to VCS)
    private static final String FINDERBET_USER = "andreaschembari2@gmail.com";
    private static final String FINDERBET_PASS = "Porcamadonna12";

    public static String getFinderSurebets() {
        try {
            System.out.println("Fetching surebets from FinderBet...");

            // attempt login / nonce retrieval if credentials provided
            ensureLoggedIn();
                Request.Builder reqBuilder = new Request.Builder()
                    .url("https://www.finderbet.com/wp-json/player/v1/getItems?surebet_do_set_filter=NOPE&action-set-filtri_nonce=60d2b02d14&_wp_http_referer=%2Fplayerbet%2F&quota_minima=&quota_massima=&data_evento_da=&data_evento_a=&inizio_evento_entro=0&profitto_min=-100&tipo_puntata=tutti&punti_in_comune=0&orderBy=profitto&order=desc&page=1&lista_groupby=&valore_surebet=")
                    .get();

                if (cachedNonce != null) {
                reqBuilder.addHeader("X-WP-Nonce", cachedNonce);
                }

                Request request = reqBuilder.build();
                    //.addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36")
                    //.addHeader("Accept", "*/*")
                    //.addHeader("Accept-Language", "it-IT,it;q=0.9,en-US;q=0.8,en;q=0.7")
                    //.addHeader("Referer", "https://www.finderbet.com/playerbet/")
                    //.addHeader("X-Requested-With", "XMLHttpRequest")
                    //.addHeader("X-WP-Nonce", "1e4703efcb")  // NONCE dal browser
                    //.addHeader("Cookie", "_ga=GA1.1.688748575.1761668585; _tt_enable_cookie=1; _ttp=01K8NT2NQ9FYVRCHBMC91NY22V_.tt.1; _fbp=fb.1.1761668585366.89262094082728283; wp-wpml_current_language=it; wordpress_logged_in_cf8ac7c19b49e075d63ab6120868003a=andreaschembari2%40gmail.com%7C1763994786%7CTTRQvcVIbeKE4QPJArS6p8I3NHsx43Sirz6zwsGcRPO%7Cabd6cb2ac1534defd4d81455f0beadcaf6a179ccd4f80c1f27004c5a14409457; sso=4382%3A92200298e2c74cc36fc11a6b5799b6c602cba45dea81300ee2eb6d7efa7a7b2b; wfwaf-authcookie-9cb17d8e2fde7c3ee1b49745a093bd31=4382%7Csubscriber%7Cread%7Cf6a8f6948bd585042730f9f58ddba4ac75e33b7828b830c7102bfe418678c7af; wp-wpml_current_admin_language_20c7282f8970e374a042b231de1f9569=it; _ga_TL0BB00PLM=GS2.1.s1763821953$o4$g1$t1763822008$j5$l0$h0; ttcsid=1763821954096::ZtPS3u-sV0yhuHLwLciF.4.1763822360229.0; ttcsid_CJMVLVJC77U2Q32C7BP0=1763821954096::CFnyMTUoy3TAcC2Ye8Xo.4.1763822360229.0")

            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    return "❌ Errore HTTP: " + response.code() + " - " + response.message();

                }

                String body = response.body().string();

                JSONObject json = new JSONObject(body);

                System.out.println("Response JSON: " + json.toString());

                // --- ARRAY PRINCIPALE ---
                if (!json.has("items")) {
                    return "❌ Risposta inattesa dal server: campo 'items' mancante.";
                }

                Object itemsObj = json.get("items");
                JSONArray events;
                if (itemsObj instanceof JSONArray) {
                    events = (JSONArray) itemsObj;
                } else {
                    // il server a volte ritorna 'items' come stringa JSON-escaped o Base64-encoded
                    String itemsRaw = json.getString("items");
                    String itemsClean = StringEscapeUtils.unescapeJson(itemsRaw).trim();

                    JSONArray parsed = null;
                    // 1) try parse directly
                    try {
                        parsed = new JSONArray(itemsClean);
                    } catch (Exception ex1) {
                        // 2) try base64 decode
                        try {
                            byte[] decoded = Base64.getDecoder().decode(itemsClean);
                            String decodedStr = new String(decoded, StandardCharsets.UTF_8).trim();
                            // if decoded is a quoted JSON string, unescape again
                            if (decodedStr.startsWith("\"") && decodedStr.endsWith("\"")) {
                                decodedStr = StringEscapeUtils.unescapeJson(decodedStr.substring(1, decodedStr.length() - 1));
                            }
                            parsed = new JSONArray(decodedStr);
                        } catch (Exception ex2) {
                            // 3) as last resort try to locate first '[' and parse substring
                            int idx = itemsClean.indexOf('[');
                            if (idx >= 0) {
                                String sub = itemsClean.substring(idx);
                                parsed = new JSONArray(sub);
                            } else {
                                throw ex2;
                            }
                        }
                    }

                    events = parsed;
                }

                if (events.length() == 0) {
                    return "ℹ️ Nessuna surebet trovata.";
                }

                StringBuilder sb = new StringBuilder();

                //for (int i = 0; i < events.length(); i++) {
                for (int i = 0; i < 1; i++) {
                    JSONObject event = events.getJSONObject(i);

                    sb.append("🏟 Evento: ").append(event.optString("gruppo_evento")).append("\n");
                    sb.append("🎮 Match: ").append(event.optString("nome_evento")).append("\n");
                    sb.append("👤 Giocatore: ").append(event.optString("player_name")).append("\n");
                    sb.append("💰 Quota Min: ").append(event.optString("valore_min"))
                      .append(" | Quota Max: ").append(event.optString("valore_max")).append("\n");
                    sb.append("⏱️ Durata Surebet: ").append(event.optString("durata_surebet")).append("\n");

                    // --- ARRAY INTERNO ESCAPED ---
                    if (event.has("items")) {
                        String innerItemsRaw = event.getString("items");
                        String innerItemsClean = StringEscapeUtils.unescapeJson(innerItemsRaw);
                        JSONArray innerItems = new JSONArray(innerItemsClean);

                        sb.append("📌 Bookmakers interni:\n");
                        for (int k = 0; k < innerItems.length(); k++) {
                            JSONObject inner = innerItems.getJSONObject(k);
                                                        sb.append("   → ").append(inner.optString("bname"))
                                                            .append(" @ quota ").append(formatQuota(inner.optString("value"))).append("\n");
                        }
                    }

                    // --- BOOKMAKERS CONSOLIDATI ---
                    if (event.has("bookmakers")) {
                        JSONArray bookmakers = event.getJSONArray("bookmakers");
                        sb.append("📚 Bookmakers consolidati:\n");
                        for (int j = 0; j < bookmakers.length(); j++) {
                            JSONObject b = bookmakers.getJSONObject(j);
                            sb.append("   🔗 ").append(b.optString("bname")).append("\n");
                            sb.append("      Giocata: ").append(sanitizeDesc(b.optString("desc"))).append("\n");
                            sb.append("      Quota: ").append(formatQuota(b.optString("value"))).append("\n");
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

    private static void ensureLoggedIn() {
        try {
            // use hardcoded credentials defined above
            String user = FINDERBET_USER;
            String pass = FINDERBET_PASS;

            // If no creds provided, still try to extract nonce from public page
            Request getPage = new Request.Builder()
                    .url("https://www.finderbet.com/playerbet/")
                    .get()
                    .build();

            try (Response r = client.newCall(getPage).execute()) {
                if (r.isSuccessful() && r.body() != null) {
                    String html = r.body().string();
                    String nonce = extractNonce(html);
                    if (nonce != null) cachedNonce = nonce;
                }
            }

            if (user == null || pass == null) return; // no creds => stop here

            // perform login POST to wp-login.php
            RequestBody form = new FormBody.Builder()
                    .add("log", user)
                    .add("pwd", pass)
                    .add("rememberme", "forever")
                    .add("wp-submit", "Log In")
                    .build();

            Request loginReq = new Request.Builder()
                    .url("https://www.finderbet.com/wp-login.php")
                    .post(form)
                    .build();

            try (Response lr = client.newCall(loginReq).execute()) {
                // ignore body; cookies stored in cookieManager
            }

            // refresh page to get nonce after login
            Request getPage2 = new Request.Builder()
                    .url("https://www.finderbet.com/playerbet/")
                    .get()
                    .build();
            try (Response r2 = client.newCall(getPage2).execute()) {
                if (r2.isSuccessful() && r2.body() != null) {
                    String html2 = r2.body().string();
                    String nonce2 = extractNonce(html2);
                    if (nonce2 != null) cachedNonce = nonce2;
                }
            }

        } catch (Exception e) {
            System.err.println("Login/nonce retrieval failed: " + e.getMessage());
        }
    }

    private static String extractNonce(String html) {
        if (html == null) return null;
        // try common patterns for X-WP-Nonce in JS objects
        Pattern p = Pattern.compile("X-WP-Nonce\\\"\\s*[:=]\\s*\\\"([^\\\"]+)\\\"");
        Matcher m = p.matcher(html);
        if (m.find()) return m.group(1);

        // alternative: _wpnonce or other patterns
        Pattern p2 = Pattern.compile("_wpnonce\\\"\\s*[:=]\\s*\\\"([^\\\"]+)\\\"");
        Matcher m2 = p2.matcher(html);
        if (m2.find()) return m2.group(1);

        return null;
    }

    private static String formatQuota(String raw) {
        if (raw == null) return "";
        String s = raw.trim();
        if (s.isEmpty()) return "";

        // Remove non numeric except dot and comma
        String cleaned = s.replaceAll("[^0-9,\\.]", "");
        cleaned = cleaned.replace(',', '.');

        try {
            double v = Double.parseDouble(cleaned);
            // Some sources encode 2.83 as 2830 (multiplicative obfuscation)
            if (v >= 1000) {
                v = v / 1000.0;
            }
            return String.format(Locale.ROOT, "%.2f", v);
        } catch (Exception e) {
            return raw;
        }
    }

    private static String sanitizeDesc(String desc) {
        if (desc == null) return "";
        String d = desc.trim();
        if (d.toLowerCase(Locale.ROOT).contains("registrati") || d.toLowerCase(Locale.ROOT).contains("registrati per")) {
            return "(contenuto visibile dopo registrazione)";
        }
        return d;
    }
}
