package com.quant.bot;

public record BotRunResult(Long botId, String symbol, String signal, String action, String message, boolean dryRun) {
}
