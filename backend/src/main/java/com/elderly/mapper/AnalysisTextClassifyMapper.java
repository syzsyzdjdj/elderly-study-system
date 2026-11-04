package com.elderly.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.elderly.entity.AnalysisTextClassify;
import org.apache.ibatis.annotations.Mapper;

/**
 * 问答文本分类结果（地区 × 业务类别）
 */
@Mapper
public interface AnalysisTextClassifyMapper extends BaseMapper<AnalysisTextClassify> {
}
