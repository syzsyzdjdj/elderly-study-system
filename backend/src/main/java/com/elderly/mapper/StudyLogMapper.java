package com.elderly.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.elderly.entity.StudyLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 学习行为日志
 */
@Mapper
public interface StudyLogMapper extends BaseMapper<StudyLog> {
}
