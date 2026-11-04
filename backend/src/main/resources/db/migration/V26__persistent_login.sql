CREATE TABLE login_session (
    token_hash CHAR(64) NOT NULL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    password_stamp CHAR(64) NOT NULL,
    expires_at_ms BIGINT NOT NULL,
    last_used_at_ms BIGINT NOT NULL,
    INDEX idx_login_session_expiry (expires_at_ms),
    INDEX idx_login_session_user (user_id),
    CONSTRAINT fk_login_session_user FOREIGN KEY (user_id) REFERENCES sys_user(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
