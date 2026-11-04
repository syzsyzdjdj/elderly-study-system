package com.elderly.aspect;

import com.elderly.mapper.SysOperationLogMapper;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OperationLogAspectTest {
    @Test void nestedAndEscapedSecretsAreMaskedBeforeSerializationOrTruncation() {
        var aspect = new OperationLogAspect(mock(SysOperationLogMapper.class));
        String secret = "Synthetic-\"escaped\\password";
        String json = aspect.maskAndSerialize(new Object[]{Map.of("id", 7, "newPassword", secret,
                "nested", List.of(Map.of("oldPassword", secret, "api_key", secret, "refresh_token", secret)))});
        assertFalse(json.contains("Synthetic"));
        assertFalse(json.contains("escaped"));
        assertTrue(json.contains("\"id\":7"));
        assertTrue(json.contains("******"));
    }
}
