package com.elderly.service.impl;

import com.elderly.common.BizException;
import com.elderly.config.DeepSeekConfig;
import com.elderly.dto.ChatReply;
import com.elderly.dto.ChatTurn;
import com.elderly.entity.AiQuestionRecord;
import com.elderly.entity.FaqQa;
import com.elderly.mapper.AiQuestionRecordMapper;
import com.elderly.service.AiChatService;
import com.elderly.util.DeepSeekUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 问答优先使用审核 FAQ，其次调用可选大模型，最后使用本地兜底。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiChatServiceImpl implements AiChatService {

    private final AiQuestionRecordMapper aiQuestionRecordMapper;
    private final DeepSeekUtil deepSeekUtil;
    private final DeepSeekConfig deepSeekConfig;
    private final PointService pointService;
    private final SysConfigService sysConfigService;
    private final FaqQaService faqQaService;
    private final LocalKnowledgeService localKnowledgeService;

    /** Explain why the model cannot be called without exposing credentials. */
    private String unavailableReason() {
        if (!sysConfigService.getBool("ai_enabled", deepSeekConfig.isEnabled()))
            return "当前未启用大模型，已使用本地知识库答复。";
        if (!deepSeekUtil.hasUsableKey()) return "当前未配置大模型服务，已使用本地知识库答复。";
        return "";
    }

    @Override
    public ChatReply chat(Long userId, String question, List<ChatTurn> history) {
        // 优先级：① 人工审核 FAQ 权威问答 → ② DeepSeek 大模型 → ③ 内置本地知识库
        ChatReply reply;
        FaqQa faq = faqQaService.match(question);
        if (faq != null) {
            reply = ChatReply.faq(faq.getAnswer());
            faqQaService.incrHit(faq.getId());
            log.info("问答命中审核FAQ(id={})", faq.getId());
        } else {
            String reason = unavailableReason();
            if (reason.isEmpty()) {
                try {
                    reply = ChatReply.ai(deepSeekUtil.ask(question, history));
                } catch (BizException e) {
                    log.warn("大模型请求失败，使用本地问答");
                    reply = ChatReply.local(localKnowledgeService.answer(question), "大模型服务暂时不可用，已使用本地知识库答复。");
                }
            } else {
                reply = ChatReply.local(localKnowledgeService.answer(question), reason);
            }
        }
        // 记录问答历史
        AiQuestionRecord record = new AiQuestionRecord();
        record.setUserId(userId);
        record.setQuestion(question);
        record.setAnswer(reply.answer());
        aiQuestionRecordMapper.insert(record);
        // 问答积分（受 QA 规则每日次数上限约束，失败不影响问答主流程）
        if (userId != null) {
            try {
                pointService.awardByRule(userId, "QA", record.getId());
            } catch (Exception e) {
                log.warn("问答积分发放失败: {}", e.getMessage());
            }
        }
        return reply;
    }

}
