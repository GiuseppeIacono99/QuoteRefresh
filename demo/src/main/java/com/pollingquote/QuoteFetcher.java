package com.pollingquote;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class QuoteFetcher {
    private static final OkHttpClient client = new OkHttpClient();

    public static String getQuotes() {
        try {
            String url = "https://esempio-scommesse.com/api/quote?match=juventus-inter";
            Request request = new Request.Builder()
                    .url(url)
                    .header("User-Agent", "QuoteBot/1.0")
                    .build();

            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    return "❌ Errore HTTP: " + response.code();
                }

                String body = response.body().string();
                // Qui puoi fare parsing JSON se il sito restituisce quote in formato JSON
                return "📊 Quote aggiornate: " + body;
            }
        } catch (Exception e) {
            return "❌ Errore nel recupero quote: " + e.getMessage();
        }
    }
}
