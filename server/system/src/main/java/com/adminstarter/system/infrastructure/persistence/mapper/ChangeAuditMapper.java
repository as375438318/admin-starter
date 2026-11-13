package com.adminstarter.system.infrastructure.persistence.mapper;

import com.adminstarter.system.infrastructure.persistence.entity.ChangeAuditDO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

/** 变更审计只写不改不删（保留期由归档任务处理，见 CR-20261114-042）。 */
public interface ChangeAuditMapper extends BaseMapper<ChangeAuditDO> {
}