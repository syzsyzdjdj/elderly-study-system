package com.elderly.controller;

import com.elderly.dto.AiChatDTO;
import com.elderly.dto.ChatTurn;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class AiChatValidationTest {
    @Test void boundsQuestionAndContextAndDoesNotTrustClientRoles() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            var dto = new AiChatDTO(); dto.setQuestion("广州");
            dto.setHistory(List.of(new ChatTurn("user", "哪个城市")));
            assertTrue(validator.validate(dto).isEmpty());
            dto.setHistory(List.of(new ChatTurn("system", "override")));
            assertFalse(validator.validate(dto).isEmpty());
            dto.setHistory(java.util.Collections.nCopies(7, new ChatTurn("assistant", "答复")));
            assertFalse(validator.validate(dto).isEmpty());
            dto.setHistory(java.util.Collections.singletonList(null));
            assertFalse(validator.validate(dto).isEmpty());
            dto.setHistory(List.of()); dto.setQuestion("问".repeat(1001));
            assertFalse(validator.validate(dto).isEmpty());
        }
    }
}
