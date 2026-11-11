package com.adminstarter.system.infrastructure.persistence.mapper;

import com.adminstarter.system.infrastructure.persistence.entity.ExportTaskDO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ExportTaskMapper extends BaseMapper<ExportTaskDO> {
}
