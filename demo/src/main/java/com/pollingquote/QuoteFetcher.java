package com.pollingquote;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.json.JSONArray;
import org.json.JSONObject;
import org.apache.commons.text.StringEscapeUtils;

public class QuoteFetcher {

    private static final OkHttpClient client = new OkHttpClient();

    public static String getFinderSurebets() {
        try {
            System.out.println("Fetching surebets from FinderBet...");

            String url = "https://www.finderbet.com/wp-json/player/v1/getItems?surebet_do_set_filter=YEPPA&action-set-filtri_nonce=d85b4dcd14&_wp_http_referer=%25252525252525252525252Fplayerbet%25252525252525252525252F&bookmakers%5B%5D=2&bookmakers%5B%5D=12&bookmakers%5B%5D=15&bookmakers%5B%5D=25&bookmakers%5B%5D=27&confronto%5B%5D=2&confronto%5B%5D=5&confronto%5B%5D=9&confronto%5B%5D=11&confronto%5B%5D=12&confronto%5B%5D=15&confronto%5B%5D=45&confronto%5B%5D=25&confronto%5B%5D=27&confronto%5B%5D=38&confronto%5B%5D=48&sports%5B%5D=2&sports%5B%5D=1&sports%5B%5D=4&sports%5B%5D=5&categoria%5B%5D=201&categoria%5B%5D=202&categoria%5B%5D=203&categoria%5B%5D=204&categoria%5B%5D=205&categoria%5B%5D=209&categoria%5B%5D=213&categoria%5B%5D=214&categoria%5B%5D=501&categoria%5B%5D=502&quota_minima=&quota_massima=&data_evento_da=&data_evento_a=&inizio_evento_entro=86400&profitto_min=1&tipo_puntata=tutti&punti_in_comune=0&orderBy=profitto&order=desc&page=1&lista_groupby=&valore_surebet=";

            Request request = new Request.Builder()
                    .url(url)
                    .get()
                    .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36")
                    .addHeader("Accept", "*/*")
                    .addHeader("Accept-Language", "it-IT,it;q=0.9,en-US;q=0.8,en;q=0.7")
                    .addHeader("Referer", "https://www.finderbet.com/playerbet/")
                    .addHeader("X-Requested-With", "XMLHttpRequest")
                    .addHeader("X-WP-Nonce", "2b9180a620")  // NONCE dal browser
                    .addHeader("Cookie", "_ga=GA1.1.688748575.1761668585; _tt_enable_cookie=1; _ttp=01K8NT2NQ9FYVRCHBMC91NY22V_.tt.1; _fbp=fb.1.1761668585366.89262094082728283; wp-wpml_current_language=it; wordpress_logged_in_cf8ac7c19b49e075d63ab6120868003a=andreaschembari2%40gmail.com%7C1763994786%7CTTRQvcVIbeKE4QPJArS6p8I3NHsx43Sirz6zwsGcRPO%7Cabd6cb2ac1534defd4d81455f0beadcaf6a179ccd4f80c1f27004c5a14409457; sso=4382%3A92200298e2c74cc36fc11a6b5799b6c602cba45dea81300ee2eb6d7efa7a7b2b; wfwaf-authcookie-9cb17d8e2fde7c3ee1b49745a093bd31=4382%7Csubscriber%7Cread%7Cf6a8f6948bd585042730f9f58ddba4ac75e33b7828b830c7102bfe418678c7af; wp-wpml_current_admin_language_20c7282f8970e374a042b231de1f9569=it; _ga_TL0BB00PLM=GS2.1.s1763821953$o4$g1$t1763822008$j5$l0$h0; ttcsid=1763821954096::ZtPS3u-sV0yhuHLwLciF.4.1763822360229.0; ttcsid_CJMVLVJC77U2Q32C7BP0=1763821954096::CFnyMTUoy3TAcC2Ye8Xo.4.1763822360229.0")
                    .build();

            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    return "❌ Errore HTTP: " + response.code();
                }

                String body = response.body().string();
                System.out.println("Response body: " + body);

                JSONObject json = new JSONObject(body);

                // --- ARRAY PRINCIPALE ---
                JSONArray events = json.getJSONArray("items");

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
                              .append(" @ quota ").append(inner.optString("value")).append("\n");
                        }
                    }

                    // --- BOOKMAKERS CONSOLIDATI ---
                    if (event.has("bookmakers")) {
                        JSONArray bookmakers = event.getJSONArray("bookmakers");
                        sb.append("📚 Bookmakers consolidati:\n");
                        for (int j = 0; j < bookmakers.length(); j++) {
                            JSONObject b = bookmakers.getJSONObject(j);
                            sb.append("   🔗 ").append(b.optString("bname")).append("\n");
                            sb.append("      Giocata: ").append(b.optString("desc")).append("\n");
                            sb.append("      Quota: ").append(b.optString("value")).append("\n");
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
}
