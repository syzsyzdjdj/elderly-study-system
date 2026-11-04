package com.elderly.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.elderly.entity.AnalysisWordFreq;
import org.apache.ibatis.annotations.Mapper;

/**
 * AI 问答热点词频结果
 */
@Mapper
public interface AnalysisWordFreqMapper extends BaseMapper<AnalysisWordFreq> {
}
