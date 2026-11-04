package com.elderly.service.impl;

import com.elderly.common.BizException;
import com.elderly.dto.ResetPwdDTO;
import com.elderly.entity.SysUser;
import com.elderly.mapper.*;
import com.elderly.security.JwtUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AdminPasswordResetTest {
    @Test void explicitPasswordIsHashedAndWeakBlankOrOversizedInputCannotUpdateUser() {
        var users = mock(SysUserMapper.class);
        var encoder = new BCryptPasswordEncoder();
        var target = new SysUser(); target.setId(7L); target.setUsername("reset_fixture");
        target.setPassword(encoder.encode("Original-test-password"));
        when(users.selectById(7L)).thenReturn(target);
        when(users.updateById(any(SysUser.class))).thenReturn(1);
        var service = new SysUserServiceImpl(users, mock(SysRoleMapper.class), encoder, mock(JwtUtil.class));
        for (String invalid : new String[]{null, "", "      ", "short", "中".repeat(25)}) {
            assertThrows(BizException.class, () -> service.resetPwd(7L, invalid));
        }
        verify(users, never()).updateById(any(SysUser.class));
        assertThrows(BizException.class, () -> service.resetPwd(7L, "Original-test-password"));
        service.resetPwd(7L, "New-password-for-test");
        var saved = ArgumentCaptor.forClass(SysUser.class);
        verify(users).updateById(saved.capture());
        assertEquals(7L, saved.getValue().getId());
        assertNotEquals("New-password-for-test", saved.getValue().getPassword());
        assertTrue(encoder.matches("New-password-for-test", saved.getValue().getPassword()));
    }

    @Test void resetPayloadRequiresNewPasswordAndNeverSerializesOrPrintsIt() throws Exception {
        var mapper = new ObjectMapper();
        var dto = mapper.readValue("{\"id\":7,\"newPassword\":\"Synthetic-password-for-test\"}", ResetPwdDTO.class);
        assertEquals("Synthetic-password-for-test", dto.getNewPassword());
        assertFalse(mapper.writeValueAsString(dto).contains("Synthetic-password-for-test"));
        assertFalse(dto.toString().contains("Synthetic-password-for-test"));
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            assertTrue(validator.validate(dto).isEmpty());
            dto.setNewPassword(null);
            assertFalse(validator.validate(dto).isEmpty());
            dto.setNewPassword("short");
            assertFalse(validator.validate(dto).isEmpty());
        }
    }
}
