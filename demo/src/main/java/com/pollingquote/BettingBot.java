package com.pollingquote;

import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.ArrayList;
import java.util.List;

public class BettingBot extends TelegramLongPollingBot {

    private final String botUsername = "t.me/Surebet_Player_top_bot";
    private final String botToken = "8230679289:AAHmqLrp-mKV64j3wwaBil5TsOzcS0l1lFc";
    private Long chatIdUtente = null;

    public static void main(String[] args) throws Exception {
        TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
        BettingBot bot = new BettingBot();
        botsApi.registerBot(bot);

        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(bot::inviaQuoteAutomatiche, 0, 1, TimeUnit.MINUTES);
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasMessage() && update.getMessage().hasText()) {
            String text = update.getMessage().getText();
            Long chatId = update.getMessage().getChatId();

            if (text.equals("/start")) {
                chatIdUtente = chatId;
                sendMessage(chatId, "👋 Benvenuto! Ti invierò le quote ogni minuto.");
            } else if (text.equals("/quote")) {
                sendMessage(chatId, QuoteFetcher.getFinderSurebets());
            } else {
                sendMessage(chatId, "Scrivi /quote per vedere le quote o /start per iniziare.");
            }
        }
    }

    private void inviaQuoteAutomatiche() {
        if (chatIdUtente != null) {
            sendMessage(chatIdUtente, QuoteFetcher.getFinderSurebets());
        }
    }

    private void sendMessage(Long chatId, String text) {
        if (text == null) return;
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

            // prefer splitting at the last newline inside the chunk
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
