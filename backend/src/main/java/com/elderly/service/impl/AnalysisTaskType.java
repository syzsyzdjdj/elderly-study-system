package com.elderly.service.impl;

import java.util.Arrays;

/** 分析任务的标识、名称与说明。 */
enum AnalysisTaskType {
    KMEANS("kmeans", "学习人群聚类", "按活跃天数、日均累计分钟和用户课程完成率划分相对活跃组；提供轮廓系数"),
    COURSE_HOT("course_hot", "课程热度排行", "按课程独立学习用户排行；分类占比是用户分类参与次数分布"),
    STUDY_TREND("study_trend", "学习趋势分析", "按日期汇总独立活跃人数与增量学习分钟"),
    FP_GROWTH("fp_growth", "关联规则挖掘", "FP-Growth 算法挖掘课程共学关联（支持度/置信度/提升度）"),
    WORD_FREQ("word_freq", "问答热点词频", "jieba 分词 + 停用词过滤，统计 AI 问答高频热点词"),
    ITEM_CF("item_cf", "Item-CF 推荐", "物品协同过滤（评分归一化），生成个性化课程推荐"),
    ALS_RECOMMEND("als_recommend", "ALS 矩阵分解推荐", "ALS 隐语义模型用户-课程协同过滤推荐"),
    TEXT_CLASSIFY("text_classify", "问答文本分类", "TF-IDF + 朴素贝叶斯，按地区×业务双维度分类 AI 问答");

    final String key;
    final String title;
    final String description;
    AnalysisTaskType(String key, String title, String description) {
        this.key = key; this.title = title; this.description = description;
    }
    static AnalysisTaskType fromKey(String key) {
        return Arrays.stream(values()).filter(task -> task.key.equals(key)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("未知任务: " + key));
    }
}
