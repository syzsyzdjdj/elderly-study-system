package com.elderly.dto;

/**
 * AI 问答返回：回答内容 + 来源标识。
 * source 取值：FAQ=审核权威问答；AI=大模型回答；LOCAL=本地知识库兜底
 */
public record ChatReply(String answer, String source, String notice) {

    public ChatReply(String answer, String source) { this(answer, source, ""); }

    public static ChatReply faq(String answer) {
        return new ChatReply(answer, "FAQ");
    }

    public static ChatReply ai(String answer) {
        return new ChatReply(answer, "AI");
    }

    public static ChatReply local(String answer) {
        return new ChatReply(answer, "LOCAL");
    }

    public static ChatReply local(String answer, String notice) {
        return new ChatReply(answer, "LOCAL", notice);
    }
}
