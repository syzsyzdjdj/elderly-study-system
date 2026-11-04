package com.elderly.util;

import com.elderly.entity.CourseCategory;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.*;

/** 分类规则使用名称路径关联现有分类，主键变化不影响匹配。 */
public final class CourseCategoryRules {
    public record Rule(String categoryPath, List<String> keywords) {}
    private static final List<Rule> RULES = load();
    private CourseCategoryRules() {}

    private static List<Rule> load() {
        try (var input = CourseCategoryRules.class.getResourceAsStream("/course-category-rules.json")) {
            if (input == null) throw new IOException("Missing course-category-rules.json");
            return List.copyOf(new ObjectMapper().readValue(input, new TypeReference<List<Rule>>() {}));
        } catch (IOException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    public static Long match(String text, List<CourseCategory> categories) {
        Map<Long, CourseCategory> byId = new HashMap<>();
        categories.forEach(category -> byId.put(category.getId(), category));
        Map<String, List<Long>> targets = new HashMap<>();
        for (CourseCategory category : categories) {
            String path = path(category, byId, new HashSet<>());
            if (path != null) targets.computeIfAbsent(path, ignored -> new ArrayList<>()).add(category.getId());
        }
        String normalized = text == null ? "" : text.toLowerCase(Locale.ROOT);
        Long best = null;
        int longest = 0;
        // 子分类优先；同层使用最长命中词，长度相同则保留配置顺序。
        for (boolean child : List.of(true, false)) {
            for (Rule rule : RULES) {
                if (rule.categoryPath().contains("/") != child) continue;
                List<Long> ids = targets.get(rule.categoryPath());
                if (ids == null || ids.size() != 1) continue;
                for (String keyword : rule.keywords()) {
                    if (keyword.length() > longest && normalized.contains(keyword.toLowerCase(Locale.ROOT))) {
                        best = ids.get(0);
                        longest = keyword.length();
                    }
                }
            }
            if (best != null) return best;
        }
        return null;
    }

    private static String path(CourseCategory category, Map<Long, CourseCategory> byId, Set<Long> visited) {
        if (category == null || !Integer.valueOf(1).equals(category.getStatus())
                || category.getCategoryName() == null || !visited.add(category.getId())) return null;
        Long parentId = category.getParentId();
        if (parentId == null || parentId == 0) return category.getCategoryName();
        String parent = path(byId.get(parentId), byId, visited);
        return parent == null ? null : parent + "/" + category.getCategoryName();
    }
}
