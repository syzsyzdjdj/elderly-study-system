package com.elderly.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.elderly.entity.FaqQa;
import com.elderly.mapper.FaqQaMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 审核常见问题库服务：
 * 管理端维护（分页/保存/删除）；问答入口调用 {@link #match(String)} 优先返回人工审核答案。
 * 启用条目在内存缓存（量小），管理端写操作后失效重建。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FaqQaService {

    private final FaqQaMapper faqQaMapper;

    /** 启用问答缓存，null 表示需要加载 */
    private volatile List<FaqQa> enabledCache;

    /** 管理端分页（可按分类、状态、关键词过滤） */
    public Page<FaqQa> adminPage(long page, long size, String category, Integer status, String keyword) {
        QueryWrapper<FaqQa> wrapper = new QueryWrapper<>();
        if (category != null && !category.isBlank()) {
            wrapper.eq("category", category);
        }
        if (status != null) {
            wrapper.eq("status", status);
        }
        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(w -> w.like("question", keyword).or().like("keywords", keyword).or().like("answer", keyword));
        }
        wrapper.orderByDesc("status").orderByAsc("category").orderByDesc("id");
        return faqQaMapper.selectPage(new Page<>(page, size), wrapper);
    }

    /** 新增或修改，修改后缓存失效 */
    public void save(FaqQa faq) {
        if (faq.getQuestion() == null || faq.getQuestion().isBlank()
                || faq.getAnswer() == null || faq.getAnswer().isBlank()) {
            throw new IllegalArgumentException("问题和答案不能为空");
        }
        if (faq.getStatus() == null) {
            faq.setStatus(1);
        }
        if (faq.getId() == null) {
            faq.setHitCount(0L);
            faqQaMapper.insert(faq);
        } else {
            // 命中次数由系统维护，编辑时不让前端覆盖
            faq.setHitCount(null);
            faq.setCreateTime(null);
            faqQaMapper.updateById(faq);
        }
        enabledCache = null;
    }

    /** 删除后缓存失效 */
    public void delete(Long id) {
        faqQaMapper.deleteById(id);
        enabledCache = null;
    }

    /**
     * 匹配已审核问答：
     * 1) 问题文本归一化后与标准问法完全相等 → 直接命中；
     * 2) 否则按关键词包含数计分，取命中关键词最多（平分时关键词总长度更长者更具体）的一条。
     *
     * @return 命中的审核问答；未命中返回 null（由调用方走 AI 大模型）
     */
    public FaqQa match(String question) {
        if (question == null || question.isBlank()) {
            return null;
        }
        String q = normalize(question);
        if (q.isEmpty()) {
            return null;
        }
        FaqQa best = null;
        int bestScore = 0;
        int bestTokenLen = 0;
        for (FaqQa f : enabledList()) {
            // 完整问法相等：最高优先级
            if (!f.getQuestion().isBlank() && normalize(f.getQuestion()).equals(q)) {
                return f;
            }
            int score = 0;
            int tokenLen = 0;
            for (String raw : splitKeywords(f.getKeywords())) {
                String kw = normalize(raw);
                // 过短的词（单字）容易误伤，忽略
                if (kw.length() >= 2 && q.contains(kw)) {
                    score++;
                    tokenLen += kw.length();
                }
            }
            if (score > bestScore || (score == bestScore && score > 0 && tokenLen > bestTokenLen)) {
                best = f;
                bestScore = score;
                bestTokenLen = tokenLen;
            }
        }
        return best;
    }

    /** 命中次数 +1（原子自增，不走缓存） */
    public void incrHit(Long id) {
        try {
            faqQaMapper.update(null, new UpdateWrapper<FaqQa>()
                    .eq("id", id)
                    .setSql("hit_count = hit_count + 1"));
        } catch (Exception e) {
            log.warn("FAQ 命中次数更新失败: {}", e.getMessage());
        }
    }

    private List<FaqQa> enabledList() {
        List<FaqQa> cache = enabledCache;
        if (cache != null) {
            return cache;
        }
        synchronized (this) {
            if (enabledCache == null) {
                enabledCache = faqQaMapper.selectList(
                        new QueryWrapper<FaqQa>().eq("status", 1));
            }
            return enabledCache;
        }
    }

    /** 关键词字段拆分：兼容中英文逗号、分号、顿号、空白、换行 */
    private static List<String> splitKeywords(String keywords) {
        List<String> result = new ArrayList<>();
        if (keywords == null || keywords.isBlank()) {
            return result;
        }
        for (String s : keywords.split("[,，;；、\\s]+")) {
            if (!s.isBlank()) {
                result.add(s.trim());
            }
        }
        return result;
    }

    /** 归一化：去空白、转小写、去常见中英文标点，降低口语化表达差异影响 */
    private static String normalize(String text) {
        if (text == null) {
            return "";
        }
        return text.trim().toLowerCase()
                .replaceAll("[\\p{P}\\p{S}\\s]+", "");
    }
}
