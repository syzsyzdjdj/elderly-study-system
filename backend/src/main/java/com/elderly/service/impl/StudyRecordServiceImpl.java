package com.elderly.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.elderly.common.BizException;
import com.elderly.common.SecurityUtil;
import com.elderly.dto.StudyRecordDTO;
import com.elderly.entity.CourseInfo;
import com.elderly.entity.StudyLog;
import com.elderly.entity.StudyRecord;
import com.elderly.entity.SysUser;
import com.elderly.mapper.CourseInfoMapper;
import com.elderly.mapper.StudyLogMapper;
import com.elderly.mapper.StudyRecordMapper;
import com.elderly.mapper.SysUserMapper;
import com.elderly.service.StudyRecordService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 学习记录服务实现
 */
@Service
@RequiredArgsConstructor
public class StudyRecordServiceImpl implements StudyRecordService {

    private final StudyRecordMapper studyRecordMapper;
    private final StudyLogMapper studyLogMapper;
    private final CourseInfoMapper courseInfoMapper;
    private final SysUserMapper sysUserMapper;
    private final PointService pointService;

    @Override
    @Transactional
    public void saveRecord(StudyRecordDTO dto) {
        // 只信任 SecurityContext 中的用户，不信任前端传参
        Long userId = SecurityUtil.getCurrentUserId();
        if (userId == null) {
            throw new BizException("请先登录");
        }
        if (dto.getCourseId() == null) {
            throw new BizException("课程ID不能为空");
        }
        CourseInfo course = courseInfoMapper.selectById(dto.getCourseId());
        if (course == null) {
            throw new BizException("课程不存在");
        }

        int progress = dto.getProgress() == null ? 0 : Math.max(0, Math.min(100, dto.getProgress()));
        int studyTime = dto.getStudyTime() == null ? 0 : Math.max(0, dto.getStudyTime());

        // 原子 upsert：不存在则插入，存在则进度取大、时长累加（并发安全，依赖唯一索引 uk_user_course）
        StudyRecord record = new StudyRecord();
        record.setUserId(userId);
        record.setCourseId(dto.getCourseId());
        record.setProgress(progress);
        record.setStudyTime(studyTime);
        record.setIsFinish(progress >= 100 ? 1 : 0);
        studyRecordMapper.upsert(record);

        // 首次完成且未发放过 → 原子抢占发放（并发下仅一个请求成功），并写积分明细
        int awarded = studyRecordMapper.markAwarded(userId, dto.getCourseId());
        if (awarded == 1) {
            int reward = course.getPointsReward() == null ? 5 : course.getPointsReward();
            pointService.recordEarn(userId, reward, "COURSE", course.getId(),
                    "完成课程：" + course.getTitle());
        }

        // 写学习行为日志（Spark 学情分析数据源，一次会话一条）
        StudyLog log = new StudyLog();
        log.setUserId(userId);
        log.setCourseId(dto.getCourseId());
        log.setStudyTime(studyTime);
        log.setProgress(progress);
        log.setIsFinish(progress >= 100 ? 1 : 0);
        log.setStudyDate(LocalDate.now());
        studyLogMapper.insert(log);
    }

    @Override
    public List<StudyRecord> listMyRecords() {
        Long userId = SecurityUtil.getCurrentUserId();
        if (userId == null) {
            throw new BizException("请先登录");
        }
        return listUserRecords(userId);
    }

    @Override
    public List<StudyRecord> listUserRecords(Long userId) {
        if (userId == null) {
            throw new BizException("用户ID不能为空");
        }
        // 从学习行为日志按课程聚合（模拟数据只写 study_log，以此为准保证所有用户可见）：
        // 进度取最大、完成取最大、时长累加、最近学习时间取最大
        List<Map<String, Object>> rows = studyLogMapper.selectMaps(
                new QueryWrapper<StudyLog>()
                        .select("course_id",
                                "MAX(progress) AS progress",
                                "MAX(is_finish) AS is_finish",
                                "SUM(study_time) AS study_time",
                                "MAX(create_time) AS last_study_time")
                        .eq("user_id", userId)
                        .groupBy("course_id")
                        .orderByDesc("last_study_time"));
        List<Long> ids = rows.stream().map(row -> asLong(row.get("course_id"))).filter(java.util.Objects::nonNull).distinct().toList();
        Map<Long, CourseInfo> courses = ids.isEmpty() ? Map.of() : courseInfoMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(CourseInfo::getId, Function.identity()));
        List<StudyRecord> result = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            StudyRecord record = new StudyRecord();
            record.setUserId(userId);
            record.setCourseId(asLong(row.get("course_id")));
            record.setProgress(asInt(row.get("progress")));
            record.setIsFinish(asInt(row.get("is_finish")));
            record.setStudyTime(asInt(row.get("study_time")));
            Object lastTime = row.get("last_study_time");
            if (lastTime instanceof LocalDateTime) {
                record.setLastStudyTime((LocalDateTime) lastTime);
            } else if (lastTime instanceof java.sql.Timestamp) {
                record.setLastStudyTime(((java.sql.Timestamp) lastTime).toLocalDateTime());
            }
            CourseInfo course = courses.get(record.getCourseId());
            if (course != null) {
                record.setCourseTitle(course.getTitle());
                record.setCover(course.getCover());
            }
            result.add(record);
        }
        return result;
    }

    private static Long asLong(Object o) {
        return o == null ? null : ((Number) o).longValue();
    }

    private static Integer asInt(Object o) {
        return o == null ? null : ((Number) o).intValue();
    }

    @Override
    public List<StudyLog> listUserLogs(Long userId) {
        if (userId == null) {
            throw new BizException("用户ID不能为空");
        }
        List<StudyLog> logs = studyLogMapper.selectList(
                new QueryWrapper<StudyLog>().eq("user_id", userId)
                        .orderByDesc("id").last("LIMIT 200"));
        List<Long> ids = logs.stream().map(StudyLog::getCourseId).filter(java.util.Objects::nonNull).distinct().toList();
        Map<Long, CourseInfo> courses = ids.isEmpty() ? Map.of() : courseInfoMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(CourseInfo::getId, Function.identity()));
        for (StudyLog log : logs) {
            CourseInfo course = courses.get(log.getCourseId());
            if (course != null) {
                log.setCourseTitle(course.getTitle());
            }
        }
        return logs;
    }

    @Override
    public List<StudyRecord> listCourseLearners(Long courseId) {
        if (courseId == null) {
            throw new BizException("课程ID不能为空");
        }
        // 从学习行为日志按用户聚合（保证模拟数据也可见）：
        // 进度取最大、完成取最大、时长累加、最近学习时间取最大
        List<Map<String, Object>> rows = studyLogMapper.selectMaps(
                new QueryWrapper<StudyLog>()
                        .select("user_id",
                                "MAX(progress) AS progress",
                                "MAX(is_finish) AS is_finish",
                                "SUM(study_time) AS study_time",
                                "MAX(create_time) AS last_study_time")
                        .eq("course_id", courseId)
                        .groupBy("user_id")
                        .orderByDesc("last_study_time"));
        List<Long> ids = rows.stream().map(row -> asLong(row.get("user_id"))).filter(java.util.Objects::nonNull).distinct().toList();
        Map<Long, SysUser> users = ids.isEmpty() ? Map.of() : sysUserMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(SysUser::getId, Function.identity()));
        List<StudyRecord> result = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            StudyRecord record = new StudyRecord();
            record.setCourseId(courseId);
            record.setUserId(asLong(row.get("user_id")));
            record.setProgress(asInt(row.get("progress")));
            record.setIsFinish(asInt(row.get("is_finish")));
            record.setStudyTime(asInt(row.get("study_time")));
            Object lastTime = row.get("last_study_time");
            if (lastTime instanceof LocalDateTime) {
                record.setLastStudyTime((LocalDateTime) lastTime);
            } else if (lastTime instanceof java.sql.Timestamp) {
                record.setLastStudyTime(((java.sql.Timestamp) lastTime).toLocalDateTime());
            }
            // 填充用户昵称
            SysUser user = users.get(record.getUserId());
            if (user != null) {
                record.setUserName(user.getNickname() != null ? user.getNickname() : user.getUsername());
            }
            result.add(record);
        }
        return result;
    }
}
