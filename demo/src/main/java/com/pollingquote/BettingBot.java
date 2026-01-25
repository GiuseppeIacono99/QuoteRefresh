package com.pollingquote;

import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

import java.util.concurrent.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class BettingBot extends TelegramLongPollingBot {

    private final String botUsername = "t.me/Surebet_Player_top_bot";
    private final String botToken = "8230679289:AAHmqLrp-mKV64j3wwaBil5TsOzcS0l1lFc";

    // Mappa chatId -> ScheduledFuture per schedulare le notifiche
    private final Map<Long, ScheduledFuture<?>> scheduledTasks = new ConcurrentHashMap<>();

    // Scheduler con thread pool per gestire più utenti
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(10);

    public static void main(String[] args) throws Exception {
        TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
        BettingBot bot = new BettingBot();
        botsApi.registerBot(bot);
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasMessage() && update.getMessage().hasText()) {
            String text = update.getMessage().getText();
            Long chatId = update.getMessage().getChatId();

            switch (text) {
                case "/start":
                    handleStart(chatId);
                    break;

                case "/stop":
                    handleStop(chatId);
                    break;

                case "/quote":
                    sendMessage(chatId, QuoteFetcher.getFinderSurebets());
                    break;

                default:
                    sendMessage(chatId, "Scrivi /quote per vedere le quote, /start per iniziare o /stop per fermare le notifiche.");
            }
        }
    }

    private void handleStart(Long chatId) {
        // Messaggio di benvenuto
        sendMessage(chatId, "👋 Benvenuto! Ti invierò le quote.");

        // Invia subito le quote
        String immediateQuote = QuoteFetcher.getFinderSurebets();
        if (!"ℹ️ Nessuna surebet trovata".equals(immediateQuote)) {
            sendMessage(chatId, immediateQuote);
        }

        // (ri)avvia la schedulazione per questo utente
        scheduledTasks.compute(chatId, (id, existingTask) -> {
            if (existingTask != null && !existingTask.isCancelled()) {
                existingTask.cancel(false);
            }

            return scheduler.scheduleAtFixedRate(() -> {
                sendMessage(id, QuoteFetcher.getFinderSurebets());
            }, 15, 15, TimeUnit.SECONDS);
        });
    }

    private void handleStop(Long chatId) {
        ScheduledFuture<?> task = scheduledTasks.remove(chatId);
        if (task != null) {
            task.cancel(false);
            sendMessage(chatId, "✅ Le notifiche automatiche sono state fermate.");
        } else {
            sendMessage(chatId, "ℹ️ Non c'erano notifiche attive.");
        }
    }

    private void sendMessage(Long chatId, String text) {
        if (text == null) return;
        if ("ℹ️ Nessuna nuova surebet da inviare".equals(text.trim())) return;

        final int TELEGRAM_MAX = 4096;

        try {
            List<String> parts = splitMessage(text, TELEGRAM_MAX);
            for (String part : parts) {
                SendMessage msg = new SendMessage(chatId.toString(), part);
                execute(msg);
            }
        } catch (TelegramApiException e) {
            System.err.println("Errore invio messaggio: " + e.getMessage());
        }
    }

    private List<String> splitMessage(String text, int maxChars) {
        List<String> parts = new ArrayList<>();
        int cpCount = text.codePointCount(0, text.length());
        int startCp = 0;

        while (startCp < cpCount) {
            int endCp = Math.min(startCp + maxChars, cpCount);
            int startIndex = text.offsetByCodePoints(0, startCp);
            int endIndex = text.offsetByCodePoints(0, endCp);

            int splitIndex = -1;
            for (int i = endIndex; i > startIndex; i--) {
                if (text.charAt(i - 1) == '\n') { splitIndex = i; break; }
            }

            if (splitIndex > startIndex) {
                parts.add(text.substring(startIndex, splitIndex));
                startCp = text.codePointCount(0, splitIndex);
            } else {
                parts.add(text.substring(startIndex, endIndex));
                startCp = endCp;
            }
        }

        return parts;
    }

    @Override
    public String getBotUsername() {
        return botUsername;
    }

    @Override
    public String getBotToken() {
        return botToken;
    }
}
