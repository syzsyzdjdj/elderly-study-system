package com.elderly.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.elderly.entity.StudyRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 学习记录 Mapper
 */
@Mapper
public interface StudyRecordMapper extends BaseMapper<StudyRecord> {

    /**
     * 原子 upsert：不存在则插入，存在则进度取大、时长累加（唯一索引 uk_user_course 保证并发安全）
     * VALUES(列) 语法在 MySQL 8 全版本可用
     */
    @Update("INSERT INTO study_record (user_id, course_id, progress, is_finish, study_time, last_study_time, create_time, awarded) " +
            "VALUES (#{userId}, #{courseId}, #{progress}, #{isFinish}, #{studyTime}, NOW(), NOW(), 0) " +
            "ON DUPLICATE KEY UPDATE progress = GREATEST(progress, VALUES(progress)), " +
            "study_time = study_time + VALUES(study_time), " +
            "is_finish = IF(GREATEST(progress, VALUES(progress)) >= 100, 1, is_finish), " +
            "last_study_time = NOW()")
    int upsert(StudyRecord record);

    /**
     * 原子抢占发放完成积分：仅"已完成且从未发放"的记录能更新成功，返回受影响行数
     */
    @Update("UPDATE study_record SET awarded = 1 " +
            "WHERE user_id = #{userId} AND course_id = #{courseId} AND is_finish = 1 AND awarded = 0")
    int markAwarded(@Param("userId") Long userId, @Param("courseId") Long courseId);
}
