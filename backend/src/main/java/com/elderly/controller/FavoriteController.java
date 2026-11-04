package com.elderly.controller;

import com.elderly.common.Result;
import com.elderly.common.SecurityUtil;
import com.elderly.entity.CourseInfo;
import com.elderly.service.impl.FavoriteService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 课程收藏（老年学员）
 */
@RestController
@RequestMapping("/api/favorite")
@RequiredArgsConstructor
public class FavoriteController {

    private final FavoriteService favoriteService;

    private Long userId() {
        Long id = SecurityUtil.getCurrentUserId();
        if (id == null) {
            throw new com.elderly.common.BizException("请先登录");
        }
        return id;
    }

    /** 我的收藏课程 */
    @GetMapping("/list")
    public Result<List<CourseInfo>> list() {
        return Result.success(favoriteService.myFavorites(userId()));
    }

    /** 收藏课程 */
    @PostMapping("/add/{courseId}")
    public Result<Void> add(@PathVariable Long courseId) {
        favoriteService.add(userId(), courseId);
        return Result.success();
    }

    /** 取消收藏 */
    @DeleteMapping("/{courseId}")
    public Result<Void> remove(@PathVariable Long courseId) {
        favoriteService.remove(userId(), courseId);
        return Result.success();
    }

    /** 是否已收藏 */
    @GetMapping("/check/{courseId}")
    public Result<Map<String, Boolean>> check(@PathVariable Long courseId) {
        return Result.success(Map.of("favorite", favoriteService.isFavorite(userId(), courseId)));
    }
}
