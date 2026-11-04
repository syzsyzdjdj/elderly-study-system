package com.elderly.controller;

import com.elderly.common.Result;
import com.elderly.dto.StudyRecordDTO;
import com.elderly.entity.StudyLog;
import com.elderly.entity.StudyRecord;
import com.elderly.service.StudyRecordService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 学习记录（仅操作当前登录用户自己的数据）
 */
@RestController
@RequestMapping("/api/study/record")
@RequiredArgsConstructor
public class StudyRecordController {

    private final StudyRecordService studyRecordService;

    /** 保存学习进度（upsert，progress 取大值、时长累加、首次完成发放积分） */
    @PostMapping("/save")
    public Result<Void> save(@Valid @RequestBody StudyRecordDTO dto) {
        studyRecordService.saveRecord(dto);
        return Result.success();
    }

    /** 当前用户学习记录列表（含课程标题） */
    @GetMapping("/list")
    public Result<List<StudyRecord>> list() {
        return Result.success(studyRecordService.listMyRecords());
    }

    /** 管理员查看指定用户学过哪些课程（每门课一条汇总：进度/完成/累计时长） */
    @GetMapping("/admin/user/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<List<StudyRecord>> userRecords(@PathVariable Long userId) {
        return Result.success(studyRecordService.listUserRecords(userId));
    }

    /** 管理员查看指定用户的学习行为明细（每次学习会话：日期/课程/时长/进度） */
    @GetMapping("/admin/logs/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<List<StudyLog>> userLogs(@PathVariable Long userId) {
        return Result.success(studyRecordService.listUserLogs(userId));
    }

    /** 管理员查看指定课程被哪些用户学过（按用户聚合：进度/时长/完成/昵称） */
    @GetMapping("/admin/course/{courseId}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<List<StudyRecord>> courseLearners(@PathVariable Long courseId) {
        return Result.success(studyRecordService.listCourseLearners(courseId));
    }
}
