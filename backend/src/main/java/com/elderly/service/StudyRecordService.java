package com.elderly.service;

import com.elderly.dto.StudyRecordDTO;
import com.elderly.entity.StudyLog;
import com.elderly.entity.StudyRecord;

import java.util.List;

/**
 * 学习记录服务
 */
public interface StudyRecordService {

    /** 保存学习进度(upsert，progress 取大值、study_time 累加、首次完成发放积分) */
    void saveRecord(StudyRecordDTO dto);

    /** 当前用户的学习记录(含课程标题) */
    List<StudyRecord> listMyRecords();

    /** 管理员查看指定用户的课程学习汇总(每门课一条，含课程标题) */
    List<StudyRecord> listUserRecords(Long userId);

    /** 管理员查看指定用户的学习行为明细(每次学习会话一条，最近在前，含课程标题) */
    List<StudyLog> listUserLogs(Long userId);

    /** 管理员查看指定课程被哪些用户学过（按用户聚合：进度/时长/完成/昵称） */
    List<StudyRecord> listCourseLearners(Long courseId);
}
