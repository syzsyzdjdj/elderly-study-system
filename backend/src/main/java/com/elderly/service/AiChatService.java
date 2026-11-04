package com.elderly.service;

import com.elderly.dto.ChatReply;
import com.elderly.dto.ChatTurn;
import java.util.List;

/**
 * AI 问答服务：审核 FAQ 权威问答优先，其次大模型，最后本地知识库兜底
 */
public interface AiChatService {

    /**
     * 问答：优先匹配人工审核常见问题库，未命中走大模型/本地知识库；
     * 全程记录问答历史，命中来源见 {@link ChatReply#source()}。
     *
     * @param userId   当前用户ID
     * @param question 用户提问
     * @return 回答内容与来源
     */
    ChatReply chat(Long userId, String question, List<ChatTurn> history);

    default ChatReply chat(Long userId, String question) {
        return chat(userId, question, List.of());
    }
}
