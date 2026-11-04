package com.elderly.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 分地区学情统计（sys_user 关联 study_log 聚合）
 */
@Mapper
public interface RegionStatsMapper {

    /** 按省聚合：学员数、学习人数、学习行为数、学习总时长、完成次数 */
    @Select("""
            SELECT COALESCE(u.province, '未知') AS province,
                   COUNT(DISTINCT u.id) AS userCount,
                   COUNT(DISTINCT l.user_id) AS learnerCount,
                   COUNT(l.id) AS logCount,
                   COALESCE(SUM(l.study_time), 0) AS studyTime,
                   COALESCE(SUM(l.is_finish), 0) AS finishCount
            FROM sys_user u
            JOIN sys_role r ON r.id = u.role_id AND r.role_key = 'user'
            LEFT JOIN study_log l ON l.user_id = u.id
            GROUP BY u.province
            ORDER BY userCount DESC
            """)
    List<Map<String, Object>> provinceStats();

    /** 按市聚合（指定省下钻） */
    @Select("""
            SELECT COALESCE(u.city, '未知') AS city,
                   COUNT(DISTINCT u.id) AS userCount,
                   COUNT(DISTINCT l.user_id) AS learnerCount,
                   COUNT(l.id) AS logCount,
                   COALESCE(SUM(l.study_time), 0) AS studyTime,
                   COALESCE(SUM(l.is_finish), 0) AS finishCount
            FROM sys_user u
            JOIN sys_role r ON r.id = u.role_id AND r.role_key = 'user'
            LEFT JOIN study_log l ON l.user_id = u.id
            WHERE u.province = #{province}
            GROUP BY u.city
            ORDER BY userCount DESC
            """)
    List<Map<String, Object>> cityStats(@Param("province") String province);
}
