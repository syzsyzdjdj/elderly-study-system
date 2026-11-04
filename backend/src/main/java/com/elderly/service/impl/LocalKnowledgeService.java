package com.elderly.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.regex.Pattern;

/** Content is maintained in local-qa.json; matching is deterministic and testable. */
@Service
public class LocalKnowledgeService {
    public record Entry(List<String> keywords, String answer, int priority) {}
    public record Knowledge(String fallback, List<String> weakKeywords, List<Entry> entries) {}
    private final Knowledge knowledge;
    private final Set<String> weakKeywords;

    public LocalKnowledgeService(ObjectMapper mapper) {
        try (var input = new ClassPathResource("local-qa.json").getInputStream()) {
            knowledge = mapper.readValue(input, Knowledge.class);
            weakKeywords = knowledge.weakKeywords() == null ? Set.of() : knowledge.weakKeywords().stream()
                    .map(LocalKnowledgeService::normalize).collect(Collectors.toUnmodifiableSet());
            if (knowledge.fallback() == null || knowledge.fallback().isBlank()
                    || knowledge.entries() == null || knowledge.entries().isEmpty())
                throw new IllegalArgumentException("Empty local knowledge");
            for (Entry entry : knowledge.entries()) {
                if (entry.answer() == null || entry.answer().isBlank() || entry.keywords() == null
                        || entry.keywords().isEmpty()) throw new IllegalArgumentException("Invalid local answer");
            }
        } catch (Exception e) { throw new IllegalStateException("本地问答资源加载失败", e); }
    }

    public String answer(String question) {
        String text = normalize(question);
        Entry best = null;
        int bestLength = 0;
        for (Entry entry : knowledge.entries()) {
            int length = entry.keywords().stream().map(LocalKnowledgeService::normalize).distinct()
                    .filter(keyword -> matches(text, keyword))
                    .mapToInt(keyword -> weakKeywords.contains(keyword) ? 1 : keyword.length()).max().orElse(0);
            if (length > 0 && (best == null || entry.priority() > best.priority()
                    || (entry.priority() == best.priority() && length > bestLength))) {
                best = entry;
                bestLength = length;
            }
        }
        return best == null ? knowledge.fallback() : best.answer();
    }

    private static String normalize(String value) {
        return value == null ? "" : Normalizer.normalize(value, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT).trim();
    }

    private static boolean matches(String text, String keyword) {
        if (keyword.isBlank()) return false;
        // "hi" must not match "this"; Chinese phrases still support substring matching.
        if (keyword.matches("[a-z0-9]+"))
            return Pattern.compile("(?<![a-z0-9])" + Pattern.quote(keyword) + "(?![a-z0-9])").matcher(text).find();
        return text.contains(keyword);
    }
}
