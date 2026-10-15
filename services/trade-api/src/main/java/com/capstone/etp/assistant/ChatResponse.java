package com.capstone.etp.assistant;

import java.util.List;

/**
 * The answer. advisoryBlocked and redirectTo are set when the question asked for advice. answeredBy says who wrote
 * the sentence: GEMINI, or RULES when the model was unavailable or its answer was not used. The cards are always the data.
 */
public record ChatResponse(
        String answer,
        AssistantIntent intent,
        boolean advisoryBlocked,
        String redirectTo,
        List<DataCard> dataCards,
        List<QuickAction> quickActions,
        List<String> citations,
        String conversationId,
        String answeredBy) {
}
