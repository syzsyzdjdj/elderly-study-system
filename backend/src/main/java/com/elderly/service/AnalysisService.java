package com.elderly.service;

import com.elderly.entity.AnalysisAssociationRule;
import com.elderly.entity.AnalysisCategoryShare;
import com.elderly.entity.AnalysisCourseHot;
import com.elderly.entity.AnalysisRecommend;
import com.elderly.entity.AnalysisRecommendAls;
import com.elderly.entity.AnalysisResult;
import com.elderly.entity.AnalysisStudyTrend;
import com.elderly.entity.AnalysisTextClassify;
import com.elderly.entity.AnalysisWordFreq;
import com.elderly.entity.CourseInfo;

import java.util.List;
import java.util.Map;

/**
 * 学情分析服务（Spark：K-Means 聚类 / 课程热度 / 学习趋势 / 关联规则 / 热点词频 / 个性化推荐）
 */
public interface AnalysisService {

    /** 运行聚类分析并落库，返回全表最新结果 */
    List<AnalysisResult> runCluster();

    /** 直接返回 analysis_result 数据 */
    List<AnalysisResult> getLatestResult();

    /** 运行课程热度分析并落库，返回排行结果 */
    List<AnalysisCourseHot> runCourseHot();

    /** 直接返回课程热度排行 */
    List<AnalysisCourseHot> getCourseHot();

    /** 直接返回分类占比 */
    List<AnalysisCategoryShare> getCategoryShare();

    /** 运行学习趋势分析并落库，返回趋势结果 */
    List<AnalysisStudyTrend> runStudyTrend();

    /** 直接返回学习趋势 */
    List<AnalysisStudyTrend> getStudyTrend();

    /** 同一只读快照中的趋势结果、来源和批次。 */
    Map<String, Object> getStudyTrendSnapshot();

    /** 运行关联规则挖掘并落库，返回规则结果 */
    List<AnalysisAssociationRule> runAssociation();

    /** 直接返回关联规则 */
    List<AnalysisAssociationRule> getAssociation();

    /** 运行热点词频统计并落库，返回词频结果 */
    List<AnalysisWordFreq> runWordFreq();

    /** 直接返回热点词频 */
    List<AnalysisWordFreq> getWordFreq();

    /** 运行个性化推荐并落库，返回推荐结果 */
    List<AnalysisRecommend> runRecommend();

    /** 运行 ALS 矩阵分解个性化推荐（用户-课程协同过滤）并落库，返回推荐结果 */
    List<AnalysisRecommendAls> runAlsRecommend();

    /** 直接返回 ALS 个性化推荐结果 */
    List<AnalysisRecommendAls> getAlsRecommend();

    /** 运行问答文本分类（朴素贝叶斯，地区×业务双维度）并落库，返回分类统计结果 */
    List<AnalysisTextClassify> runTextClassify();

    /** 直接返回问答文本分类结果 */
    List<AnalysisTextClassify> getTextClassify();

    /** 依次运行全部 8 个分析任务，返回每个任务的执行结果（单任务失败不中断） */
    List<Map<String, Object>> runAll();

    /** 单独运行指定分析任务，返回执行结果（成功/失败 + 耗时） */
    Map<String, Object> runSingle(String task);

    /** Spark 分析任务目录：任务元数据 + 各结果表条数/最近运行时间 */
    List<Map<String, Object>> getTaskCatalog();

    /** 获取当前分析进度（供前端轮询） */
    Map<String, Object> getProgress();

    /** 混合推荐（匿名传 null）；过滤完整已学历史，附来源与原因，候选不足时补充热门/最新。 */
    List<CourseInfo> recommendCourses(Long userId);
}
