-- 学习计划表
CREATE TABLE IF NOT EXISTS learning_plan (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL COMMENT '用户ID',
    library_id BIGINT NOT NULL COMMENT '关联的知识库ID',
    title VARCHAR(200) NOT NULL COMMENT '计划标题',
    goal TEXT COMMENT '学习目标',
    profile JSON COMMENT '用户画像JSON',
    agents JSON COMMENT '智能体状态JSON',
    status TINYINT DEFAULT 0 COMMENT '状态: 0-正常, 1-已删除',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_user_id (user_id),
    INDEX idx_library_id (library_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='学习计划表';

-- 学习阶段表
CREATE TABLE IF NOT EXISTS learning_stage (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    plan_id BIGINT NOT NULL COMMENT '关联的学习计划ID',
    stage_order INT NOT NULL COMMENT '阶段顺序',
    title VARCHAR(200) NOT NULL COMMENT '阶段标题',
    duration VARCHAR(50) COMMENT '预计时长',
    goal TEXT COMMENT '阶段目标',
    resources JSON COMMENT '资源名称列表JSON',
    status VARCHAR(20) DEFAULT 'pending' COMMENT '状态: done-完成, active-进行中, pending-待开始',
    INDEX idx_plan_id (plan_id),
    INDEX idx_stage_order (plan_id, stage_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='学习阶段表';

-- 学习资源表
CREATE TABLE IF NOT EXISTS learning_resource (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    plan_id BIGINT NOT NULL COMMENT '关联的学习计划ID',
    group_type VARCHAR(50) NOT NULL COMMENT '资源分组: 文档/结构图/练习/实操',
    title VARCHAR(200) NOT NULL COMMENT '资源标题',
    description TEXT COMMENT '资源描述',
    action VARCHAR(50) DEFAULT '查看' COMMENT '操作按钮文本',
    INDEX idx_plan_id (plan_id),
    INDEX idx_group_type (plan_id, group_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='学习资源表';
