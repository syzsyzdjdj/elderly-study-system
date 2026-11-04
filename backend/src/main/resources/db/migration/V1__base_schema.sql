-- V1: 空库基础结构与公开课程目录。后续 V2-V24 继续完成版本迁移。
-- 不包含用户、密码、学习日志、问答或分析结果；管理员通过首次启动配置创建。
-- 只由 Flyway 在空库执行；已有 baseline=1 的库会跳过本文件。

CREATE TABLE `ai_question_record` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键编号',
  `user_id` bigint DEFAULT NULL COMMENT '提问用户编号（关联sys_user）',
  `question` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci COMMENT '用户提问文本（词频与业务分类输入）',
  `answer` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci COMMENT '智能问答系统回答文本',
  `create_time` datetime DEFAULT NULL COMMENT '记录创建时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='智能问答记录（用户提问与系统回答）';

CREATE TABLE `analysis_association_rule` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键编号',
  `antecedent_ids` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '关联规则前件课程编号（逗号分隔）',
  `antecedent_titles` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '前件课程标题',
  `consequent_ids` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '关联规则后件课程编号（逗号分隔）',
  `consequent_titles` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '后件课程标题',
  `confidence` double DEFAULT NULL COMMENT '置信度（接触前件用户中也接触后件的比例，0至1）',
  `support` double DEFAULT NULL COMMENT '支持度（同时接触前后件的用户比例，0至1）',
  `create_time` datetime DEFAULT NULL COMMENT '分析结果入库时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='Spark课程共学关联规则（支持度、置信度、提升度）';

CREATE TABLE `analysis_category_share` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键编号',
  `category_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '课程分类名称',
  `study_count` bigint DEFAULT NULL COMMENT '分析窗口内该分类独立学习用户数',
  `share_rate` double DEFAULT NULL COMMENT '分类参与占比（百分数；分母为各分类独立用户数之和，用户可跨分类）',
  `create_time` datetime DEFAULT NULL COMMENT '分析结果入库时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='Spark课程分类参与分布结果（用户可跨分类参与）';

CREATE TABLE `analysis_course_hot` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键编号',
  `rank_no` int DEFAULT NULL COMMENT '按独立学习用户数排序的名次',
  `course_id` bigint DEFAULT NULL COMMENT '课程编号',
  `title` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '课程标题快照',
  `study_count` bigint DEFAULT NULL COMMENT '分析窗口内课程独立学习用户数（非日志数或会话数）',
  `create_time` datetime DEFAULT NULL COMMENT '分析结果入库时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='Spark课程热度结果（课程独立学习用户排行）';

CREATE TABLE `analysis_recommend` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键编号',
  `user_id` bigint DEFAULT NULL COMMENT '接收推荐的用户编号',
  `course_id` bigint DEFAULT NULL COMMENT '推荐课程编号',
  `title` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '推荐课程标题快照',
  `score` double DEFAULT NULL COMMENT '用户候选内归一化排序分数（0至1，非概率，不跨用户比较）',
  `create_time` datetime DEFAULT NULL COMMENT '分析结果入库时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='Spark物品协同过滤课程推荐结果（ItemCF）';

CREATE TABLE `analysis_recommend_als` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键编号',
  `user_id` bigint DEFAULT NULL COMMENT '接收推荐的用户编号',
  `course_id` bigint DEFAULT NULL COMMENT '推荐课程编号',
  `title` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '推荐课程标题快照',
  `score` double DEFAULT NULL COMMENT 'ALS隐式反馈模型排序分数（非概率）',
  `create_time` datetime DEFAULT NULL COMMENT '分析结果入库时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='Spark隐式反馈矩阵分解课程推荐结果（ALS）';

CREATE TABLE `analysis_result` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键编号',
  `cluster_id` int DEFAULT NULL COMMENT '模型簇编号（编号本身不代表活跃度高低）',
  `cluster_name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '相对活跃度分组名称',
  `user_count` int DEFAULT NULL COMMENT '该组参与分析的用户数',
  `avg_study_time` double DEFAULT NULL COMMENT '组内用户活跃日平均累计分钟的均值',
  `avg_finish_rate` double DEFAULT NULL COMMENT '组内用户课程完成率的均值（0至1，非完成日志比例）',
  `description` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '分组解释与适用限制',
  `create_time` datetime DEFAULT NULL COMMENT '分析结果入库时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='Spark学习人群聚类结果（相对活跃度分组）';

CREATE TABLE `analysis_study_trend` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键编号',
  `study_date` date DEFAULT NULL COMMENT '学习统计日期',
  `study_count` bigint DEFAULT NULL COMMENT '当日独立活跃学习用户数',
  `study_duration_min` bigint DEFAULT NULL COMMENT '当日上报新增学习分钟数之和',
  `create_time` datetime DEFAULT NULL COMMENT '分析结果入库时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='Spark每日学习趋势结果（活跃用户数与增量分钟）';

CREATE TABLE `analysis_word_freq` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键编号',
  `rank_no` int DEFAULT NULL COMMENT '热点词排名',
  `word` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '分词后的热点词',
  `freq` bigint DEFAULT NULL COMMENT '包含该词的提问条数（单条提问重复词仅计一次）',
  `create_time` datetime DEFAULT NULL COMMENT '分析结果入库时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='Spark问答热点词频结果（包含该词的提问条数）';

CREATE TABLE `course_category` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键编号',
  `parent_id` bigint DEFAULT '0' COMMENT '父分类编号（0或空表示顶级分类）',
  `category_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '课程分类中文名称',
  `sort` int DEFAULT NULL,
  `status` tinyint DEFAULT '1',
  `create_time` datetime DEFAULT NULL COMMENT '记录创建时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='课程分类（含父子分类关系）';

CREATE TABLE `course_info` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键编号',
  `category_id` bigint DEFAULT NULL COMMENT '所属课程分类编号',
  `title` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '课程标题（用于展示与文本分类弱监督训练）',
  `cover` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '封面图',
  `content` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci COMMENT '课程图文内容（HTML）',
  `video_url` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '视频地址',
  `intro` text COLLATE utf8mb4_general_ci COMMENT '简介',
  `duration` int DEFAULT NULL COMMENT '预估学习时长（分钟）',
  `points_reward` int DEFAULT '5' COMMENT '完成奖励积分',
  `status` tinyint DEFAULT '1' COMMENT '课程状态（0下架，1上架；Spark只分析上架课程）',
  `create_time` datetime DEFAULT NULL COMMENT '记录创建时间',
  `update_time` datetime DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='课程信息（标题、内容、分类及上架状态）';

CREATE TABLE `study_log` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键编号',
  `user_id` bigint NOT NULL COMMENT '学习用户编号（关联sys_user）',
  `course_id` bigint NOT NULL COMMENT '学习课程编号（关联course_info）',
  `study_time` int DEFAULT '0' COMMENT '本次上报新增学习分钟数（非累计时长；日志不等于学习会话）',
  `progress` int DEFAULT '0' COMMENT '本次上报课程进度（0至100）',
  `is_finish` tinyint DEFAULT '0' COMMENT '本次上报是否完成（0未完成，1已完成）',
  `study_date` date DEFAULT NULL COMMENT '学习日期（用于活跃天数及每日趋势统计）',
  `create_time` datetime DEFAULT NULL COMMENT '记录创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_log_user` (`user_id`),
  KEY `idx_log_date` (`study_date`),
  KEY `idx_log_course` (`course_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='学习行为日志（增量上报，Spark分析数据源）';

CREATE TABLE `study_record` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `course_id` bigint NOT NULL,
  `progress` int DEFAULT '0' COMMENT '学习进度百分比',
  `is_finish` tinyint DEFAULT '0' COMMENT '0未完成 1已完成',
  `awarded` tinyint NOT NULL DEFAULT '0' COMMENT '完成积分是否已发放 0否1是',
  `study_time` int DEFAULT '0' COMMENT '累计学习分钟数',
  `last_study_time` datetime DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_course` (`user_id`,`course_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='学习记录表';

CREATE TABLE `sys_menu` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `parent_id` bigint DEFAULT NULL,
  `menu_name` varchar(100) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `path` varchar(100) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `component` varchar(200) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `perms` varchar(100) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '权限标识',
  `sort` int DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='菜单权限表';

CREATE TABLE `sys_role` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `role_name` varchar(50) COLLATE utf8mb4_general_ci NOT NULL,
  `role_key` varchar(50) COLLATE utf8mb4_general_ci NOT NULL COMMENT 'admin/course_manager/user',
  `remark` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  UNIQUE KEY `uk_role_key` (`role_key`),
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='角色表';

CREATE TABLE `sys_role_menu` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `role_id` bigint DEFAULT NULL,
  `menu_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='角色菜单关联表';

CREATE TABLE `sys_user` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键编号',
  `username` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '用户登录账号',
  `password` varchar(100) COLLATE utf8mb4_general_ci NOT NULL COMMENT 'BCrypt加密密码',
  `nickname` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '用户昵称',
  `phone` varchar(20) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '手机号',
  `avatar` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '头像地址',
  `role_id` bigint DEFAULT NULL COMMENT '角色ID',
  `points` int DEFAULT '0' COMMENT '学习积分',
  `voice` varchar(50) COLLATE utf8mb4_general_ci DEFAULT 'default' COMMENT '朗读音色key',
  `status` tinyint DEFAULT '1' COMMENT '用户状态（0停用，1启用）',
  `create_time` datetime DEFAULT NULL COMMENT '记录创建时间',
  `update_time` datetime DEFAULT NULL,
  UNIQUE KEY `uk_user_username` (`username`),
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='系统用户（含常住地区、启用状态及模拟用户标记）';

INSERT INTO sys_role (id,role_name,role_key,remark,create_time) VALUES
(1,'管理员','admin','平台管理',CURRENT_TIMESTAMP),
(2,'课程管理员','course_manager','课程内容管理',CURRENT_TIMESTAMP),
(3,'学员','user','学习用户',CURRENT_TIMESTAMP);

INSERT INTO `sys_menu` (`id`,`parent_id`,`menu_name`,`path`,`component`,`perms`,`sort`,`create_time`) VALUES
(1,0,'数据看板','/admin/dashboard','admin/Dashboard.vue','dashboard',1,CURRENT_TIMESTAMP),
(2,0,'用户管理','/admin/users','admin/Users.vue','user:manage',2,CURRENT_TIMESTAMP),
(3,0,'课程管理','/admin/courses','admin/Courses.vue','course:manage',3,CURRENT_TIMESTAMP),
(4,0,'分类管理','/admin/categories','admin/Categories.vue','category:manage',4,CURRENT_TIMESTAMP),
(5,0,'角色权限','/admin/roles','admin/Roles.vue','role:manage',5,CURRENT_TIMESTAMP),
(6,0,'系统设置','/admin/settings','admin/Settings.vue','system:setting',6,CURRENT_TIMESTAMP);

INSERT INTO sys_role_menu (role_id,menu_id) SELECT 1,id FROM sys_menu;

INSERT INTO `course_category` (`id`,`parent_id`,`category_name`,`sort`,`status`,`create_time`) VALUES
(1,0,'智能手机操作',1,1,CURRENT_TIMESTAMP),
(2,0,'生活服务',2,1,CURRENT_TIMESTAMP),
(3,0,'反诈防骗',3,1,CURRENT_TIMESTAMP),
(4,0,'健康养生',4,1,CURRENT_TIMESTAMP),
(5,0,'休闲娱乐',5,1,CURRENT_TIMESTAMP),
(11,1,'基础操作',1,1,CURRENT_TIMESTAMP),
(12,1,'社交聊天',2,1,CURRENT_TIMESTAMP),
(13,1,'支付购物',3,1,CURRENT_TIMESTAMP),
(14,1,'出行交通',4,1,CURRENT_TIMESTAMP),
(21,2,'就医挂号',1,1,CURRENT_TIMESTAMP),
(22,2,'政务办事',2,1,CURRENT_TIMESTAMP),
(23,2,'便民缴费',3,1,CURRENT_TIMESTAMP),
(31,3,'电信诈骗',1,1,CURRENT_TIMESTAMP),
(32,3,'网络安全',2,1,CURRENT_TIMESTAMP),
(41,4,'运动锻炼',1,1,CURRENT_TIMESTAMP),
(42,4,'饮食营养',2,1,CURRENT_TIMESTAMP),
(51,5,'影音娱乐',1,1,CURRENT_TIMESTAMP),
(52,5,'兴趣爱好',2,1,CURRENT_TIMESTAMP);

INSERT INTO `course_info` (`id`,`category_id`,`title`,`intro`,`duration`,`points_reward`,`status`,`create_time`) VALUES
(1,22,'手机完成养老金资格认证','用手机完成养老金资格认证，全程图文讲解，几分钟学会。',12,5,1,CURRENT_TIMESTAMP),
(2,21,'医保电子凭证申领与使用','申领医保电子凭证，看病买药一部手机全搞定。',10,5,1,CURRENT_TIMESTAMP),
(3,12,'微信视频通话入门','学会微信视频通话，随时和子女、老友面对面。',10,5,1,CURRENT_TIMESTAMP),
(4,11,'手机字体调大与声音设置','字太小看不清？声音太小听不见？本课教你调。',8,5,1,CURRENT_TIMESTAMP),
(5,31,'识破常见电信诈骗套路','牢记防骗口诀，守好养老钱。',12,5,1,CURRENT_TIMESTAMP),
(6,21,'网上预约挂号操作指南','足不出户挂专家号，告别大清早排队。',10,5,1,CURRENT_TIMESTAMP),
(7,21,'异地就医备案办理指南','去外地看病住院，手机办备案，报销不用来回跑。',10,5,1,CURRENT_TIMESTAMP),
(8,22,'电子社保卡领取与使用','视频演示电子社保卡领取全流程，跟着做一遍就会。',9,5,1,CURRENT_TIMESTAMP),
(9,12,'微信发红包与收付款','过年过节发红包，出门买菜扫码付，一学就会。',8,5,1,CURRENT_TIMESTAMP),
(10,11,'手机拍照与照片管理','学会拍照记录生活，分享给儿女看，其乐融融。',8,5,1,CURRENT_TIMESTAMP),
(11,11,'微信支付安全设置','视频演示微信支付安全设置，守住钱袋子。',8,5,1,CURRENT_TIMESTAMP),
(12,31,'保健品骗局识别指南','认清保健品套路，捂紧养老钱袋子。',10,5,1,CURRENT_TIMESTAMP),
(13,32,'网络谣言识别与应对','谣言止于智者，转发之前先查一查。',8,5,1,CURRENT_TIMESTAMP),
(14,31,'刷单返利骗局警示','视频还原刷单骗局全过程，看完不再上当。',9,5,1,CURRENT_TIMESTAMP),
(15,23,'水电燃气线上缴费','水电气缴费不出门，手机一点就完成。',8,5,1,CURRENT_TIMESTAMP),
(16,14,'网约车与公交出行','视频演示手机打车全流程，出行不求人。',8,5,1,CURRENT_TIMESTAMP),
(17,21,'在线问诊与药店买药','小病线上问医生，买药可用医保码。',8,5,1,CURRENT_TIMESTAMP);
