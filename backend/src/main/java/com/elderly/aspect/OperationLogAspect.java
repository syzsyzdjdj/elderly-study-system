package com.elderly.aspect;

import com.elderly.entity.SysOperationLog;
import com.elderly.mapper.SysOperationLogMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 管理员操作日志切面：自动记录后台所有写操作（POST/PUT/DELETE）。
 * 只记录 ADMIN / COURSE_MANAGER 角色的操作（学员端操作不记入审计日志）。
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class OperationLogAspect {

    private final SysOperationLogMapper operationLogMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** URL 前缀 → 功能模块名 */
    private static final Map<String, String> MODULE_MAP = new HashMap<>();

    static {
        MODULE_MAP.put("/api/course", "课程管理");
        MODULE_MAP.put("/api/category", "分类管理");
        MODULE_MAP.put("/api/user", "用户管理");
        MODULE_MAP.put("/api/role", "角色权限");
        MODULE_MAP.put("/api/menu", "菜单管理");
        MODULE_MAP.put("/api/analysis", "学情分析");
        MODULE_MAP.put("/api/tts", "语音管理");
        MODULE_MAP.put("/api/announcement", "公告管理");
        MODULE_MAP.put("/api/prize", "积分福利");
        MODULE_MAP.put("/api/exchange", "积分福利");
        MODULE_MAP.put("/api/gov", "政务管理");
        MODULE_MAP.put("/api/dict", "数据字典");
        MODULE_MAP.put("/api/config", "参数配置");
        MODULE_MAP.put("/api/companion", "陪伴话术");
        MODULE_MAP.put("/api/upload", "文件上传");
    }

    /** 不记录审计的路径（含敏感参数或无管理意义） */
    private static boolean ignore(String uri) {
        return uri.startsWith("/api/login") || uri.startsWith("/api/register")
                || uri.startsWith("/api/logout") || uri.startsWith("/api/reminder")
                || uri.startsWith("/api/favorite") || uri.startsWith("/api/point")
                || uri.startsWith("/api/ai") || uri.startsWith("/api/study")
                || uri.startsWith("/api/message");
    }

    @Pointcut("execution(public * com.elderly.controller..*.*(..))")
    public void controllers() {
    }

    @Around("controllers()")
    public Object around(ProceedingJoinPoint pjp) throws Throwable {
        HttpServletRequest request = currentRequest();
        if (request == null) {
            return pjp.proceed();
        }
        String method = request.getMethod();
        String uri = request.getRequestURI();
        boolean writeMethod = "POST".equalsIgnoreCase(method) || "PUT".equalsIgnoreCase(method)
                || "DELETE".equalsIgnoreCase(method);
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        boolean admin = authentication != null && authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(a -> a.equals("ROLE_ADMIN") || a.equals("ROLE_COURSE_MANAGER"));
        if (!writeMethod || !admin || ignore(uri)) {
            return pjp.proceed();
        }

        long start = System.currentTimeMillis();
        SysOperationLog logEntry = new SysOperationLog();
        logEntry.setUserId(toLong(authentication.getPrincipal()));
        logEntry.setUsername(String.valueOf(authentication.getDetails()));
        logEntry.setModule(resolveModule(uri));
        logEntry.setRequestMethod(method);
        logEntry.setRequestUrl(uri);
        logEntry.setIp(resolveIp(request));
        try {
            logEntry.setRequestParams(maskAndSerialize(pjp.getArgs()));
        } catch (Exception e) {
            logEntry.setRequestParams("参数序列化失败");
        }

        Object result;
        try {
            result = pjp.proceed();
            logEntry.setStatus(1);
            return result;
        } catch (Throwable e) {
            logEntry.setStatus(0);
            String msg = e.getMessage();
            logEntry.setErrorMsg(msg == null ? e.getClass().getSimpleName()
                    : (msg.length() > 900 ? msg.substring(0, 900) : msg));
            throw e;
        } finally {
            logEntry.setCostTime(System.currentTimeMillis() - start);
            logEntry.setCreateTime(LocalDateTime.now());
            try {
                operationLogMapper.insert(logEntry);
            } catch (Exception ex) {
                log.warn("操作日志写入失败: {}", ex.getMessage());
            }
        }
    }

    private HttpServletRequest currentRequest() {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attrs == null ? null : attrs.getRequest();
    }

    private String resolveModule(String uri) {
        return MODULE_MAP.entrySet().stream()
                .filter(e -> uri.startsWith(e.getKey()))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse("其他");
    }

    private String resolveIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }

    /** 参数脱敏（密码/密钥不入库），超长截断 */
    String maskAndSerialize(Object[] args) {
        if (args == null || args.length == 0) {
            return "";
        }
        try {
            JsonNode tree = objectMapper.valueToTree(args);
            maskSecrets(tree);
            String json = objectMapper.writeValueAsString(tree);
            return json.length() > 2000 ? json.substring(0, 2000) + "..." : json;
        } catch (Exception e) {
            return "";
        }
    }

    private void maskSecrets(JsonNode node) {
        if (node.isObject()) {
            ObjectNode object = (ObjectNode) node;
            var names = new java.util.ArrayList<String>();
            object.fieldNames().forEachRemaining(names::add);
            for (String name : names) {
                String normalized = name.replaceAll("[-_]", "").toLowerCase(java.util.Locale.ROOT);
                if (normalized.contains("password") || normalized.contains("secret") || normalized.endsWith("token")
                        || java.util.Set.of("pwd", "oldpwd", "newpwd", "apikey", "authorization").contains(normalized)) {
                    object.put(name, "******");
                } else maskSecrets(object.get(name));
            }
        } else if (node.isArray()) node.forEach(this::maskSecrets);
    }

    private Long toLong(Object principal) {
        if (principal instanceof Long l) {
            return l;
        }
        try {
            return Long.valueOf(String.valueOf(principal));
        } catch (Exception e) {
            return null;
        }
    }
}
