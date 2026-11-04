package com.elderly.controller;

import com.elderly.common.Result;
import com.elderly.service.impl.ClassificationReviewService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/analysis/classification-review")
@PreAuthorize("hasAnyRole('ADMIN','COURSE_MANAGER')")
@RequiredArgsConstructor
public class ClassificationReviewController {
    private final ClassificationReviewService service;

    @GetMapping
    public Result<Map<String, Object>> page(@RequestParam(defaultValue="1") int page,
            @RequestParam(defaultValue="20") int size, @RequestParam(defaultValue="true") boolean onlyPending,
            @RequestParam(defaultValue="all") String predictionGroup) {
        return Result.success(service.page(page, size, onlyPending, predictionGroup));
    }

    @PostMapping("/{id}")
    public Result<Void> save(@PathVariable long id, @RequestBody LabelRequest label) {
        service.save(id, label.getCategory(), label.getQuestion(), label.getReviewToken()); return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result<Void> remove(@PathVariable long id, @RequestBody LabelRequest label) {
        service.remove(id, label.getQuestion(), label.getReviewToken()); return Result.success();
    }

    @Data public static class LabelRequest {
        private String category;
        private String question;
        private String reviewToken;
    }
}
