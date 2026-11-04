-- V24 用户显示参数扩展：字体加粗 + 行距档位
-- 幂等：列已存在时跳过（与 V2 相同的 p_add_col 模式，用完即删）
USE `elderly_study`;

DROP PROCEDURE IF EXISTS p_add_col;
DELIMITER $$
CREATE PROCEDURE p_add_col(IN t VARCHAR(64), IN c VARCHAR(64), IN ddl VARCHAR(500))
BEGIN
  IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = t AND COLUMN_NAME = c) THEN
    SET @s = CONCAT('ALTER TABLE `', t, '` ADD COLUMN ', ddl);
    PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;
  END IF;
END$$
DELIMITER ;

CALL p_add_col('sys_user', 'font_bold', "`font_bold` TINYINT NOT NULL DEFAULT 0 COMMENT '字体加粗 0常规 1加粗'");
CALL p_add_col('sys_user', 'line_height', "`line_height` INT NOT NULL DEFAULT 160 COMMENT '行距百分比 140紧凑/160标准/190宽松'");

DROP PROCEDURE IF EXISTS p_add_col;
