package com.elderly.controller;

import com.elderly.common.Result;
import com.elderly.entity.AnalysisAssociationRule;
import com.elderly.entity.AnalysisCategoryShare;
import com.elderly.entity.AnalysisCourseHot;
import com.elderly.entity.AnalysisRecommendAls;
import com.elderly.entity.AnalysisResult;
import com.elderly.entity.AnalysisStudyTrend;
import com.elderly.entity.AnalysisTextClassify;
import com.elderly.entity.AnalysisWordFreq;
import com.elderly.mapper.RegionStatsMapper;
import com.elderly.service.AnalysisService;
import com.elderly.service.MockDataService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 学情分析（ADMIN 或 COURSE_MANAGER）
 * /studyCluster 触发 K-Means 并返回结果（原有行为）；
 * /courseHot 等 GET 返回已落库的最新分析结果；
 * POST /runAll 依次运行全部 6 个 Spark 任务（local 内嵌 / YARN 集群，由 spark.mode 决定）。
 */
@RestController
@RequestMapping("/api/analysis")
@PreAuthorize("hasAnyRole('ADMIN','COURSE_MANAGER')")
@RequiredArgsConstructor
public class AnalysisController {

    private final AnalysisService analysisService;
    private final MockDataService mockDataService;
    private final RegionStatsMapper regionStatsMapper;

    /**
     * 分地区学情统计：默认返回省级汇总；传 province 时附带该省市级下钻。
     * 指标：学员数/学习人数/学习行为数/学习总时长(分钟)/完成次数
     */
    @GetMapping("/regionStats")
    public Result<Map<String, Object>> regionStats(@RequestParam(required = false) String province) {
        Map<String, Object> result = new HashMap<>();
        result.put("province", regionStatsMapper.provinceStats());
        if (province != null && !province.isEmpty()) {
            result.put("city", regionStatsMapper.cityStats(province));
        }
        return Result.success(result);
    }

    /** 运行 Spark K-Means 聚类分析，返回 analysis_result 全表最新结果 */
    @GetMapping("/studyCluster")
    public Result<List<AnalysisResult>> studyCluster() {
        return Result.success(analysisService.runCluster());
    }

    /** 直接返回 analysis_result 数据（不触发分析） */
    @GetMapping("/result")
    public Result<List<AnalysisResult>> result() {
        return Result.success(analysisService.getLatestResult());
    }

    /** 课程热度排行（最新落库结果） */
    @GetMapping("/courseHot")
    public Result<List<AnalysisCourseHot>> courseHot() {
        return Result.success(analysisService.getCourseHot());
    }

    /** 课程分类占比（最新落库结果） */
    @GetMapping("/categoryShare")
    public Result<List<AnalysisCategoryShare>> categoryShare() {
        return Result.success(analysisService.getCategoryShare());
    }

    /** 学习趋势（最新落库结果） */
    @GetMapping("/studyTrend")
    public Result<List<AnalysisStudyTrend>> studyTrend() {
        return Result.success(analysisService.getStudyTrend());
    }

    @GetMapping("/studyTrendSnapshot")
    public Result<Map<String, Object>> studyTrendSnapshot() {
        return Result.success(analysisService.getStudyTrendSnapshot());
    }

    /** 课程关联规则（最新落库结果） */
    @GetMapping("/association")
    public Result<List<AnalysisAssociationRule>> association() {
        return Result.success(analysisService.getAssociation());
    }

    /** 问答热点词频（最新落库结果） */
    @GetMapping("/wordFreq")
    public Result<List<AnalysisWordFreq>> wordFreq() {
        return Result.success(analysisService.getWordFreq());
    }

    /** 一键运行全部分析任务（K-Means/热度/趋势/关联规则/词频/Item-CF/ALS），返回各任务执行结果 */
    @PostMapping("/runAll")
    public Result<List<Map<String, Object>>> runAll() {
        return Result.success(analysisService.runAll());
    }

    /** ALS 矩阵分解个性化推荐结果（最新落库结果，按用户分组） */
    @GetMapping("/alsRecommend")
    public Result<List<AnalysisRecommendAls>> alsRecommend() {
        return Result.success(analysisService.getAlsRecommend());
    }

    /** 问答文本分类结果（朴素贝叶斯：地区 × 业务双维度统计，最新落库结果） */
    @GetMapping("/textClassify")
    public Result<List<AnalysisTextClassify>> textClassify() {
        return Result.success(analysisService.getTextClassify());
    }

    /** 一键分析进度（供前端轮询进度条） */
    @GetMapping("/progress")
    public Result<Map<String, Object>> progress() {
        return Result.success(analysisService.getProgress());
    }

    /** Spark 任务目录：8 个任务的元数据 + 结果表条数/最近运行时间 */
    @GetMapping("/tasks")
    public Result<List<Map<String, Object>>> tasks() {
        return Result.success(analysisService.getTaskCatalog());
    }

    /** 单独触发一个 Spark 分析任务 */
    @PostMapping("/run/{task}")
    public Result<Map<String, Object>> runTask(@PathVariable String task) {
        return Result.success(analysisService.runSingle(task));
    }

    /** 生成模拟数据（仅 ADMIN）：虚拟学员 + 学习行为日志 + 模拟 AI 提问 */
    @PostMapping("/genData")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Map<String, Object>> genData(@RequestParam(defaultValue = "500") int users,
                                               @RequestParam(defaultValue = "100000") int logs,
                                               @RequestParam(defaultValue = "2000") int aiQuestions) {
        return Result.success(mockDataService.generate(users, logs, aiQuestions));
    }

    /** 清理模拟数据（仅 ADMIN） */
    @PostMapping("/clearMockData")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Map<String, Object>> clearMockData() {
        return Result.success(mockDataService.clear());
    }
}
