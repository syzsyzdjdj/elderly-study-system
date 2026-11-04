package com.elderly.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.elderly.common.BizException;
import com.elderly.common.SecurityUtil;
import com.elderly.config.TtsVoices;
import com.elderly.entity.CourseCategory;
import com.elderly.entity.CourseInfo;
import com.elderly.entity.StudyRecord;
import com.elderly.mapper.CourseCategoryMapper;
import com.elderly.mapper.CourseInfoMapper;
import com.elderly.mapper.StudyRecordMapper;
import com.elderly.service.CourseService;
import com.elderly.util.ChatTtsUtil;
import com.elderly.util.CourseCategoryRules;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 课程服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CourseServiceImpl implements CourseService {

    private final CourseCategoryMapper courseCategoryMapper;
    private final CourseInfoMapper courseInfoMapper;
    private final StudyRecordMapper studyRecordMapper;
    private final ChatTtsUtil chatTtsUtil;
    private final FavoriteService favoriteService;

    /** 课程语音预生成队列：课程保存后后台生成音频（不阻塞保存请求） */
    private final ExecutorService ttsPregenExecutor =
            new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(64),
                    r -> new Thread(r, "tts-course-pregen"));
    private final Set<Long> queuedCourses = ConcurrentHashMap.newKeySet();

    @PreDestroy
    public void shutdown() {
        ttsPregenExecutor.shutdownNow();
        queuedCourses.clear();
    }

    /** 与前端 Study.vue listenText 完全一致的文本转换：去 HTML 标签、合并空白 */
    static String toListenText(CourseInfo course) {
        String text = course.getContent() == null ? "" : course.getContent()
                .replaceAll("<[^>]+>", " ")
                .replaceAll("\\s+", " ")
                .trim();
        if (text.isEmpty() && course.getIntro() != null) {
            text = course.getIntro();
        }
        if (text.isEmpty() && course.getTitle() != null) {
            text = course.getTitle();
        }
        return text;
    }

    /** 后台预生成课程朗读音频（全部音色：普通话+方言），已有缓存时秒回无副作用 */
    private void pregenTtsAsync(Long courseId) {
        if (!queuedCourses.add(courseId)) return;
        try {
            ttsPregenExecutor.submit(() -> {
                // 运行中的课程允许再排一次，以便保存期间发生的内容修改最终得到处理。
                queuedCourses.remove(courseId);
                try {
                    CourseInfo fresh = courseInfoMapper.selectById(courseId);
                    if (fresh == null || fresh.getStatus() == null || fresh.getStatus() != 1) {
                        return;
                    }
                    String text = toListenText(fresh);
                    if (text.isEmpty()) {
                        return;
                    }
                    // 逐个音色预生成：学员切换任一语音包即可秒播；单个音色失败（如方言需联网）不影响其他
                    for (String voice : TtsVoices.allKeys()) {
                        try {
                            chatTtsUtil.generate(text, null, voice, null, null, null);
                        } catch (Exception e) {
                            log.warn("课程语音预生成失败 courseId={} voice={}: {}", courseId, voice, e.getMessage());
                        }
                    }
                } catch (Exception e) {
                    log.warn("课程语音预生成失败 courseId={}: {}", courseId, e.getMessage());
                }
            });
        } catch (RejectedExecutionException full) {
            queuedCourses.remove(courseId);
            log.warn("课程预生成队列已满或关闭 courseId={}，打开课程时按需生成", courseId);
        }
    }

    @Override
    public List<CourseCategory> listCategoryTree() {
        List<CourseCategory> all = courseCategoryMapper.selectList(
                new QueryWrapper<CourseCategory>().orderByAsc("sort"));
        Map<Long, List<CourseCategory>> childrenMap = all.stream()
                .collect(Collectors.groupingBy(c -> c.getParentId() == null ? 0L : c.getParentId()));
        List<CourseCategory> tree = childrenMap.getOrDefault(0L, new ArrayList<>());
        buildCategoryTree(tree, childrenMap);
        return tree;
    }

    /** 递归挂载子分类 */
    private void buildCategoryTree(List<CourseCategory> nodes, Map<Long, List<CourseCategory>> childrenMap) {
        for (CourseCategory node : nodes) {
            node.setChildren(childrenMap.getOrDefault(node.getId(), new ArrayList<>()));
            buildCategoryTree(node.getChildren(), childrenMap);
        }
    }

    @Override
    public void addCategory(CourseCategory category) {
        if (category.getCategoryName() == null || category.getCategoryName().isEmpty()) {
            throw new BizException("分类名称不能为空");
        }
        if (category.getParentId() == null) {
            category.setParentId(0L);
        }
        if (category.getStatus() == null) {
            category.setStatus(1);
        }
        if (category.getSort() == null) {
            category.setSort(0);
        }
        courseCategoryMapper.insert(category);
    }

    @Override
    public void updateCategory(CourseCategory category) {
        if (category.getId() == null) {
            throw new BizException("分类ID不能为空");
        }
        if (courseCategoryMapper.selectById(category.getId()) == null) {
            throw new BizException("分类不存在");
        }
        courseCategoryMapper.updateById(category);
    }

    @Override
    public void deleteCategory(Long id) {
        if (courseCategoryMapper.selectById(id) == null) {
            throw new BizException("分类不存在");
        }
        Long childCount = courseCategoryMapper.selectCount(
                new QueryWrapper<CourseCategory>().eq("parent_id", id));
        if (childCount != null && childCount > 0) {
            throw new BizException("该分类下存在子分类，请先删除子分类");
        }
        Long courseCount = courseInfoMapper.selectCount(
                new QueryWrapper<CourseInfo>().eq("category_id", id));
        if (courseCount != null && courseCount > 0) {
            throw new BizException("该分类下存在课程，无法删除");
        }
        courseCategoryMapper.deleteById(id);
    }

    // ======================== 自动分类 ========================

    @Override
    public Map<String, Integer> autoCategorize() {
        List<CourseCategory> allCats = courseCategoryMapper.selectList(null);

        // 取所有课程（包括已分类的，重新匹配以覆盖空分类）
        List<CourseInfo> courses = courseInfoMapper.selectList(null);
        int matched = 0;
        int unmatched = 0;

        for (CourseInfo course : courses) {
            String text = ((course.getTitle() == null ? "" : course.getTitle())
                    + " " + (course.getIntro() == null ? "" : course.getIntro())).toLowerCase();

            Long matchedId = CourseCategoryRules.match(text, allCats);
            if (matchedId != null) {
                if (!matchedId.equals(course.getCategoryId())) {
                    course.setCategoryId(matchedId);
                    courseInfoMapper.updateById(course);
                }
                matched++;
            } else {
                unmatched++;
            }
        }

        log.info("自动分类完成：扫描 {} 门课程，匹配 {}，未匹配 {}", courses.size(), matched, unmatched);
        Map<String, Integer> result = new HashMap<>();
        result.put("total", courses.size());
        result.put("matched", matched);
        result.put("unmatched", unmatched);
        return result;
    }

    @Override
    public Page<CourseInfo> pageCourses(long page, long size, Long categoryId, String keyword,
                                        Integer status, String province, String city) {
        QueryWrapper<CourseInfo> wrapper = new QueryWrapper<>();
        if (categoryId != null) {
            // 选中顶级分类时包含其所有子分类的课程（分类树为两级结构）
            List<Long> categoryIds = new ArrayList<>();
            categoryIds.add(categoryId);
            courseCategoryMapper.selectList(new QueryWrapper<CourseCategory>()
                            .eq("parent_id", categoryId))
                    .forEach(c -> categoryIds.add(c.getId()));
            wrapper.in("category_id", categoryIds);
        }
        if (keyword != null && !keyword.isEmpty()) {
            wrapper.like("title", keyword);
        }
        if (status != null) {
            wrapper.eq("status", status);
        }
        // 表单留空会提交空字符串；NULL、空串及历史纯空白值都表示通用范围。
        if (province != null && !province.isBlank()) {
            wrapper.and(w -> w.isNull("province").or().apply("TRIM(province) = ''")
                    .or().eq("province", province.trim()));
            if (city != null && !city.isBlank()) {
                wrapper.and(w -> w.isNull("city").or().apply("TRIM(city) = ''")
                        .or().eq("city", city.trim()));
            }
        }
        wrapper.orderByDesc("id");
        Page<CourseInfo> result = courseInfoMapper.selectPage(new Page<>(page, size), wrapper);
        // 填充分类名称与当前用户收藏标记
        Long userId = SecurityUtil.getCurrentUserId();
        List<Long> categoryIds = result.getRecords().stream().map(CourseInfo::getCategoryId)
                .filter(java.util.Objects::nonNull).distinct().toList();
        Map<Long, String> names = new HashMap<>();
        if (!categoryIds.isEmpty()) courseCategoryMapper.selectBatchIds(categoryIds)
                .forEach(category -> names.put(category.getId(), category.getCategoryName()));
        Set<Long> favorites = favoriteService.favoriteIds(userId,
                result.getRecords().stream().map(CourseInfo::getId).toList());
        result.getRecords().forEach(c -> {
            c.setCategoryName(names.get(c.getCategoryId()));
            c.setFavorite(favorites.contains(c.getId()));
        });
        return result;
    }

    @Override
    public void addCourse(CourseInfo course) {
        if (course.getTitle() == null || course.getTitle().isEmpty()) {
            throw new BizException("课程标题不能为空");
        }
        if (course.getStatus() == null) {
            course.setStatus(1);
        }
        if (course.getPointsReward() == null) {
            course.setPointsReward(5);
        }
        if (course.getDuration() == null) {
            course.setDuration(0);
        }
        trimRegions(course);
        courseInfoMapper.insert(course);
        // 后台预生成朗读音频，学员打开课程即可秒播
        pregenTtsAsync(course.getId());
    }

    @Override
    public void updateCourse(CourseInfo course) {
        if (course.getId() == null) {
            throw new BizException("课程ID不能为空");
        }
        if (courseInfoMapper.selectById(course.getId()) == null) {
            throw new BizException("课程不存在");
        }
        trimRegions(course);
        courseInfoMapper.updateById(course);
        // 内容可能变化：重新预生成朗读音频（未变化时缓存命中，开销极小）
        pregenTtsAsync(course.getId());
    }

    @Override
    public void deleteCourse(Long id) {
        if (courseInfoMapper.selectById(id) == null) {
            throw new BizException("课程不存在");
        }
        Long recordCount = studyRecordMapper.selectCount(
                new QueryWrapper<StudyRecord>().eq("course_id", id));
        if (recordCount != null && recordCount > 0) {
            throw new BizException("该课程已存在学习记录，建议下架处理");
        }
        courseInfoMapper.deleteById(id);
    }

    /** 保留null为未提交；显式空字符串允许编辑时清除地区限制。 */
    private static void trimRegions(CourseInfo course) {
        if (course.getProvince() != null) course.setProvince(course.getProvince().trim());
        if (course.getCity() != null) course.setCity(course.getCity().trim());
        if (course.getDistrict() != null) course.setDistrict(course.getDistrict().trim());
    }

    @Override
    public Map<String, Object> detail(Long id) {
        CourseInfo course = courseInfoMapper.selectById(id);
        if (course == null) {
            throw new BizException("课程不存在");
        }
        fillCategoryName(course);
        Map<String, Object> map = new HashMap<>();
        // 附带当前用户学习进度与收藏状态（未登录返回 null/false）
        Long userId = SecurityUtil.getCurrentUserId();
        course.setFavorite(userId != null && favoriteService.isFavorite(userId, id));
        Integer progress = null;
        if (userId != null) {
            StudyRecord record = studyRecordMapper.selectOne(
                    new QueryWrapper<StudyRecord>().eq("user_id", userId).eq("course_id", id));
            if (record != null) {
                progress = record.getProgress();
            }
        }
        map.put("course", course);
        map.put("progress", progress);
        return map;
    }

    /** 填充课程分类名称 */
    private void fillCategoryName(CourseInfo course) {
        if (course.getCategoryId() != null) {
            CourseCategory category = courseCategoryMapper.selectById(course.getCategoryId());
            if (category != null) {
                course.setCategoryName(category.getCategoryName());
            }
        }
    }
}
