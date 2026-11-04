package com.elderly.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.elderly.common.BizException;
import com.elderly.entity.CourseFavorite;
import com.elderly.entity.CourseInfo;
import com.elderly.mapper.CourseFavoriteMapper;
import com.elderly.mapper.CourseInfoMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 课程收藏服务
 */
@Service
@RequiredArgsConstructor
public class FavoriteService {

    private final CourseFavoriteMapper favoriteMapper;
    private final CourseInfoMapper courseInfoMapper;

    /** 收藏（已存在则幂等忽略） */
    public void add(Long userId, Long courseId) {
        if (courseInfoMapper.selectById(courseId) == null) {
            throw new BizException("课程不存在");
        }
        Long exists = favoriteMapper.selectCount(new QueryWrapper<CourseFavorite>()
                .eq("user_id", userId).eq("course_id", courseId));
        if (exists != null && exists > 0) {
            return;
        }
        CourseFavorite favorite = new CourseFavorite();
        favorite.setUserId(userId);
        favorite.setCourseId(courseId);
        favoriteMapper.insert(favorite);
    }

    /** 取消收藏 */
    public void remove(Long userId, Long courseId) {
        favoriteMapper.delete(new QueryWrapper<CourseFavorite>()
                .eq("user_id", userId).eq("course_id", courseId));
    }

    /** 是否已收藏 */
    public boolean isFavorite(Long userId, Long courseId) {
        if (userId == null) {
            return false;
        }
        Long count = favoriteMapper.selectCount(new QueryWrapper<CourseFavorite>()
                .eq("user_id", userId).eq("course_id", courseId));
        return count != null && count > 0;
    }

    public Set<Long> favoriteIds(Long userId, Collection<Long> courseIds) {
        if (userId == null || courseIds.isEmpty()) return Set.of();
        return favoriteMapper.selectList(new QueryWrapper<CourseFavorite>().eq("user_id", userId)
                .in("course_id", courseIds)).stream().map(CourseFavorite::getCourseId).collect(Collectors.toSet());
    }

    /** 我的收藏课程列表（按收藏时间倒序，仅保留上架课程） */
    public List<CourseInfo> myFavorites(Long userId) {
        List<CourseFavorite> favorites = favoriteMapper.selectList(new QueryWrapper<CourseFavorite>()
                .eq("user_id", userId).orderByDesc("id"));
        if (favorites.isEmpty()) {
            return Collections.emptyList();
        }
        List<Long> ids = favorites.stream().map(CourseFavorite::getCourseId).toList();
        List<CourseInfo> courses = courseInfoMapper.selectBatchIds(ids);
        // 按收藏顺序排列，下架课程不展示
        List<CourseInfo> ordered = new ArrayList<>();
        for (Long id : ids) {
            courses.stream().filter(c -> c.getId().equals(id) && c.getStatus() != null && c.getStatus() == 1)
                    .findFirst().ifPresent(ordered::add);
        }
        return ordered;
    }
}
