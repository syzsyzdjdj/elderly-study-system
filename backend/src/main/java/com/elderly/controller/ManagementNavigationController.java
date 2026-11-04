package com.elderly.controller;

import com.elderly.common.Result;
import com.elderly.common.SecurityUtil;
import com.elderly.service.impl.ManagementNavigationService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequiredArgsConstructor
public class ManagementNavigationController {
    private final ManagementNavigationService navigation;

    @GetMapping("/api/menu/mine")
    @PreAuthorize("hasAnyRole('ADMIN','COURSE_MANAGER')")
    public Result<List<ManagementNavigationService.Menu>> mine() {
        return Result.success(navigation.forUser(SecurityUtil.getCurrentUserId()));
    }
}
