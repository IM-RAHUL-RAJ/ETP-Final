package com.capstone.etp.assistant;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** What the customer typed, or the quick action they pressed (then the message is the action's own wording). */
public record ChatRequest(
        @NotBlank @Size(max = 500) String message,
        @Size(max = 64) String conversationId,
        @Size(max = 40) String selectedQuickAction) {
}
