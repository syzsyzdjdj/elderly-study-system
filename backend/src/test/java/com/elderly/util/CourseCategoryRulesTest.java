package com.elderly.util;

import com.elderly.entity.CourseCategory;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class CourseCategoryRulesTest {
    CourseCategory category(long id, long parent, String name) {
        var category = new CourseCategory();
        category.setId(id); category.setParentId(parent); category.setCategoryName(name); category.setStatus(1);
        return category;
    }

    @Test void longerKeywordsWinAndRecreatedCategoryIdsAreResolvedByPath() {
        var categories = List.of(category(900, 0, "智能手机操作"), category(901, 900, "社交聊天"), category(902, 900, "支付购物"));
        assertEquals(902L, CourseCategoryRules.match("微信支付安全设置", categories));
        assertEquals(901L, CourseCategoryRules.match("微信聊天", categories));
        assertEquals(900L, CourseCategoryRules.match("智能手机", categories));
    }
    @Test void ambiguousMissingDisabledAndCyclicTargetsAreNotAssigned() {
        var root = category(1, 0, "智能手机操作");
        assertNull(CourseCategoryRules.match("付款码", List.of(root)));
        var first = category(2, 1, "支付购物"); var duplicate = category(3, 1, "支付购物");
        assertNull(CourseCategoryRules.match("付款码", List.of(root, first, duplicate)));
        first.setStatus(0);
        assertNull(CourseCategoryRules.match("付款码", List.of(root, first)));
        root.setParentId(2L); first.setStatus(1);
        assertNull(CourseCategoryRules.match("付款码", List.of(root, first)));
    }
}
