-- 补齐导航定义，菜单路径作为关联键；不改动现有角色和账号。
INSERT INTO sys_menu(parent_id,menu_name,path,component,perms,sort) SELECT 0,'📊 数据看板','/admin/dashboard','admin/dashboard','',1 WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE path='/admin/dashboard');
INSERT INTO sys_menu(parent_id,menu_name,path,component,perms,sort) SELECT 0,'⚡ Spark任务','/admin/spark-tasks','admin/spark-tasks','',2 WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE path='/admin/spark-tasks');
INSERT INTO sys_menu(parent_id,menu_name,path,component,perms,sort) SELECT 0,'分类人工复核','/admin/classification-review','admin/classification-review','',3 WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE path='/admin/classification-review');
INSERT INTO sys_menu(parent_id,menu_name,path,component,perms,sort) SELECT 0,'👥 用户管理','/admin/users','admin/users','',4 WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE path='/admin/users');
INSERT INTO sys_menu(parent_id,menu_name,path,component,perms,sort) SELECT 0,'📚 课程管理','/admin/courses','admin/courses','',5 WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE path='/admin/courses');
INSERT INTO sys_menu(parent_id,menu_name,path,component,perms,sort) SELECT 0,'🗂 分类管理','/admin/categories','admin/categories','',6 WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE path='/admin/categories');
INSERT INTO sys_menu(parent_id,menu_name,path,component,perms,sort) SELECT 0,'📢 公告资讯','/admin/announcements','admin/announcements','',7 WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE path='/admin/announcements');
INSERT INTO sys_menu(parent_id,menu_name,path,component,perms,sort) SELECT 0,'✅ 审核问答库','/admin/faq','admin/faq','',8 WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE path='/admin/faq');
INSERT INTO sys_menu(parent_id,menu_name,path,component,perms,sort) SELECT 0,'🎁 积分福利','/admin/prizes','admin/prizes','',9 WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE path='/admin/prizes');
INSERT INTO sys_menu(parent_id,menu_name,path,component,perms,sort) SELECT 0,'🏛 政务管理','/admin/gov','admin/gov','',10 WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE path='/admin/gov');
INSERT INTO sys_menu(parent_id,menu_name,path,component,perms,sort) SELECT 0,'💬 陪伴话术与提醒','/admin/companion','admin/companion','',11 WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE path='/admin/companion');
INSERT INTO sys_menu(parent_id,menu_name,path,component,perms,sort) SELECT 0,'🔊 TTS音频','/admin/tts-audio','admin/tts-audio','',12 WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE path='/admin/tts-audio');
INSERT INTO sys_menu(parent_id,menu_name,path,component,perms,sort) SELECT 0,'📖 数据字典','/admin/dict','admin/dict','',13 WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE path='/admin/dict');
INSERT INTO sys_menu(parent_id,menu_name,path,component,perms,sort) SELECT 0,'🛡 角色权限','/admin/roles','admin/roles','',14 WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE path='/admin/roles');
INSERT INTO sys_menu(parent_id,menu_name,path,component,perms,sort) SELECT 0,'📝 操作日志','/admin/logs','admin/logs','',15 WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE path='/admin/logs');
INSERT INTO sys_menu(parent_id,menu_name,path,component,perms,sort) SELECT 0,'⚙️ 系统设置','/admin/settings','admin/settings','',16 WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE path='/admin/settings');
INSERT INTO sys_menu(parent_id,menu_name,path,component,perms,sort) SELECT 0,'👤 个人中心','/admin/profile','admin/profile','',17 WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE path='/admin/profile');
INSERT INTO sys_role_menu(role_id,menu_id) SELECT r.id,m.id FROM sys_role r CROSS JOIN sys_menu m WHERE r.role_key='course_manager' AND m.path IN ('/admin/courses','/admin/categories') AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id=r.id);
