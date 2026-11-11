-- 菜单路由与组件列放宽到 255，与 DTO 侧 @Size(max = 255) 对齐（Flyway V4）
-- 原 V1 定义为 VARCHAR(128)，若只放宽校验会让 129-255 字符的输入落到数据库产生 DataIntegrityViolation（500）。

ALTER TABLE sys_menu MODIFY COLUMN path VARCHAR(255) NULL;
ALTER TABLE sys_menu MODIFY COLUMN component VARCHAR(255) NULL;
