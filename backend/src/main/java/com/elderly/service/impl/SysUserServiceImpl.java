package com.elderly.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.elderly.common.BizException;
import com.elderly.dto.LoginDTO;
import com.elderly.dto.LoginResult;
import com.elderly.dto.RegisterDTO;
import com.elderly.entity.SysRole;
import com.elderly.entity.SysUser;
import com.elderly.mapper.SysRoleMapper;
import com.elderly.mapper.SysUserMapper;
import com.elderly.security.JwtUtil;
import com.elderly.service.SysUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 系统用户服务实现
 */
@Service
@RequiredArgsConstructor
public class SysUserServiceImpl implements SysUserService {

    private final SysUserMapper sysUserMapper;
    private final SysRoleMapper sysRoleMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    /** 防暴力破解：连续失败次数上限与锁定时长（分钟） */
    private static final int MAX_FAILS = 5;
    private static final long LOCK_MINUTES = 10;

    /** username → 失败计数（单机内存方案，重启清空，满足毕设演示） */
    private final Map<String, LoginFail> failMap = new ConcurrentHashMap<>();

    /** 登录失败计数 */
    private static class LoginFail {
        int count;
        long lockUntil;
    }

    @Override
    public LoginResult login(LoginDTO loginDTO) {
        String username = loginDTO.getUsername();
        LoginFail fail = failMap.get(username);
        long now = System.currentTimeMillis();
        if (fail != null && fail.lockUntil > now) {
            long waitMin = Math.max(1, (fail.lockUntil - now + 59999) / 60000);
            throw new BizException("登录失败次数过多，账号已锁定，请" + waitMin + "分钟后再试");
        }

        SysUser user = sysUserMapper.selectOne(
                new QueryWrapper<SysUser>().eq("username", username));
        if (user == null) {
            recordFailure(username);
            throw new BizException("账号不存在");
        }
        if (user.getStatus() != null && user.getStatus() == 0) {
            throw new BizException("账号已被禁用");
        }
        if (!passwordEncoder.matches(loginDTO.getPassword(), user.getPassword())) {
            recordFailure(username);
            LoginFail latest = failMap.get(username);
            int left = Math.max(0, MAX_FAILS - (latest == null ? 1 : latest.count));
            throw new BizException(left > 0
                    ? "密码错误，还可尝试" + left + "次"
                    : "密码错误次数过多，账号已锁定" + LOCK_MINUTES + "分钟");
        }
        // 登录成功：清除失败计数
        failMap.remove(username);

        // 查询角色标识
        SysRole role = user.getRoleId() == null ? null : sysRoleMapper.selectById(user.getRoleId());
        String roleKey = role == null ? "user" : role.getRoleKey();

        String token = jwtUtil.generateToken(user.getId(), user.getUsername(), roleKey);
        user.setPassword(null);
        LoginResult result = new LoginResult();
        result.setToken(token);
        result.setUserInfo(user);
        result.setRoleKey(roleKey);
        return result;
    }

    /** 记录一次登录失败，达到上限则锁定 */
    private void recordFailure(String username) {
        LoginFail fail = failMap.computeIfAbsent(username, k -> new LoginFail());
        fail.count++;
        if (fail.count >= MAX_FAILS) {
            fail.lockUntil = System.currentTimeMillis() + LOCK_MINUTES * 60_000L;
        }
    }

    @Override
    public void register(RegisterDTO registerDTO) {
        Long count = sysUserMapper.selectCount(
                new QueryWrapper<SysUser>().eq("username", registerDTO.getUsername()));
        if (count != null && count > 0) {
            throw new BizException("用户名已存在");
        }
        // 注册默认角色：user
        SysRole userRole = sysRoleMapper.selectOne(
                new QueryWrapper<SysRole>().eq("role_key", "user"));
        SysUser user = new SysUser();
        user.setUsername(registerDTO.getUsername());
        user.setPassword(passwordEncoder.encode(registerDTO.getPassword()));
        user.setNickname(registerDTO.getNickname() == null || registerDTO.getNickname().isEmpty()
                ? registerDTO.getUsername() : registerDTO.getNickname());
        user.setPhone(registerDTO.getPhone());
        user.setProvince(registerDTO.getProvince());
        user.setCity(registerDTO.getCity());
        user.setDistrict(registerDTO.getDistrict());
        user.setRoleId(userRole == null ? null : userRole.getId());
        user.setStatus(1);
        user.setPoints(0);
        user.setFontScale(100);
        user.setHighContrast(0);
        sysUserMapper.insert(user);
    }

    @Override
    public void changePassword(Long userId, String oldPassword, String newPassword) {
        if (userId == null) {
            throw new BizException("请先登录");
        }
        if (oldPassword == null || oldPassword.isEmpty() || newPassword == null || newPassword.isEmpty()) {
            throw new BizException("原密码与新密码不能为空");
        }
        if (newPassword.length() < 6) {
            throw new BizException("新密码长度不能少于6位");
        }
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            throw new BizException("用户不存在");
        }
        if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
            throw new BizException("原密码不正确");
        }
        if (passwordEncoder.matches(newPassword, user.getPassword())) {
            throw new BizException("新密码不能与原密码相同");
        }
        SysUser update = new SysUser();
        update.setId(userId);
        update.setPassword(passwordEncoder.encode(newPassword));
        sysUserMapper.updateById(update);
    }

    @Override
    public SysUser updateProfile(Long userId, SysUser profile) {
        if (userId == null) {
            throw new BizException("请先登录");
        }
        SysUser old = sysUserMapper.selectById(userId);
        if (old == null) {
            throw new BizException("用户不存在");
        }
        SysUser update = new SysUser();
        update.setId(userId);
        // 仅允许更新白名单字段：昵称/手机/头像/常住地区/适老化显示参数/音色
        update.setNickname(profile.getNickname());
        update.setPhone(profile.getPhone());
        update.setAvatar(profile.getAvatar());
        update.setProvince(profile.getProvince());
        update.setCity(profile.getCity());
        update.setDistrict(profile.getDistrict());
        if (profile.getFontScale() != null) {
            int scale = profile.getFontScale();
            // 仅接受约定的三档字号，防止非法值
            if (scale != 100 && scale != 115 && scale != 130) {
                throw new BizException("字号参数无效");
            }
            update.setFontScale(scale);
        }
        if (profile.getHighContrast() != null) {
            update.setHighContrast(profile.getHighContrast() == 0 ? 0 : 1);
        }
        if (profile.getFontBold() != null) {
            update.setFontBold(profile.getFontBold() == 0 ? 0 : 1);
        }
        if (profile.getLineHeight() != null) {
            int lh = profile.getLineHeight();
            // 仅接受约定的三档行距，防止非法值
            if (lh != 140 && lh != 160 && lh != 190) {
                throw new BizException("行距参数无效");
            }
            update.setLineHeight(lh);
        }
        if (profile.getVoice() != null) {
            update.setVoice(profile.getVoice());
        }
        sysUserMapper.updateById(update);
        SysUser latest = sysUserMapper.selectById(userId);
        latest.setPassword(null);
        return latest;
    }

    @Override
    public Page<SysUser> pageList(long page, long size, String keyword) {
        QueryWrapper<SysUser> wrapper = new QueryWrapper<>();
        if (keyword != null && !keyword.isEmpty()) {
            wrapper.and(w -> w.like("username", keyword).or().like("nickname", keyword));
        }
        wrapper.orderByDesc("id");
        Page<SysUser> result = sysUserMapper.selectPage(new Page<>(page, size), wrapper);
        // 填充角色名称/标识 + 脱敏密码
        List<SysRole> roles = sysRoleMapper.selectList(null);
        java.util.Map<Long, SysRole> roleMap = roles.stream()
                .collect(java.util.stream.Collectors.toMap(SysRole::getId, r -> r));
        result.getRecords().forEach(u -> {
            u.setPassword(null);
            if (u.getRoleId() != null) {
                SysRole role = roleMap.get(u.getRoleId());
                if (role != null) {
                    u.setRoleName(role.getRoleName());
                    u.setRoleKey(role.getRoleKey());
                }
            }
        });
        return result;
    }

    @Override
    public void addUser(SysUser user) {
        if (user.getUsername() == null || user.getUsername().isEmpty()) {
            throw new BizException("用户名不能为空");
        }
        validateAdminPassword(user.getPassword());
        Long count = sysUserMapper.selectCount(
                new QueryWrapper<SysUser>().eq("username", user.getUsername()));
        if (count != null && count > 0) {
            throw new BizException("用户名已存在");
        }
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        if (user.getRoleId() == null) {
            SysRole userRole = sysRoleMapper.selectOne(new QueryWrapper<SysRole>().eq("role_key", "user"));
            user.setRoleId(userRole == null ? null : userRole.getId());
        }
        if (user.getStatus() == null) {
            user.setStatus(1);
        }
        if (user.getPoints() == null) {
            user.setPoints(0);
        }
        sysUserMapper.insert(user);
    }

    @Override
    public void updateUser(SysUser user) {
        if (user.getId() == null) {
            throw new BizException("用户ID不能为空");
        }
        SysUser old = sysUserMapper.selectById(user.getId());
        if (old == null) {
            throw new BizException("用户不存在");
        }
        // 用户名变更时校验唯一
        if (user.getUsername() != null && !user.getUsername().equals(old.getUsername())) {
            Long count = sysUserMapper.selectCount(
                    new QueryWrapper<SysUser>().eq("username", user.getUsername()).ne("id", user.getId()));
            if (count != null && count > 0) {
                throw new BizException("用户名已存在");
            }
        }
        // 禁止前端直接改密码：password 为空则忽略（不更新密码列）
        if (user.getPassword() == null || user.getPassword().isEmpty()) {
            user.setPassword(null);
        } else {
            validateAdminPassword(user.getPassword());
            user.setPassword(passwordEncoder.encode(user.getPassword()));
        }
        sysUserMapper.updateById(user);
    }

    @Override
    public void deleteUser(Long id) {
        if (sysUserMapper.selectById(id) == null) {
            throw new BizException("用户不存在");
        }
        sysUserMapper.deleteById(id);
    }

    @Override
    public void resetPwd(Long id, String newPassword) {
        validateAdminPassword(newPassword);
        if (id == null || id <= 0) throw new BizException("用户ID无效");
        SysUser user = sysUserMapper.selectById(id);
        if (user == null) {
            throw new BizException("用户不存在");
        }
        SysUser update = new SysUser();
        update.setId(id);
        if (passwordEncoder.matches(newPassword, user.getPassword())) {
            throw new BizException("新密码不能与原密码相同");
        }
        update.setPassword(passwordEncoder.encode(newPassword));
        if (sysUserMapper.updateById(update) != 1) throw new BizException("用户不存在");
        failMap.remove(user.getUsername());
    }

    private static void validateAdminPassword(String password) {
        if (password == null || password.isBlank() || password.length() < 12
                || password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72) {
            throw new BizException("管理端设置的密码至少12字符，UTF-8编码不能超过72字节");
        }
    }
}
