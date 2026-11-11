package com.adminstarter.system.infrastructure.persistence.mapper;

import com.adminstarter.system.infrastructure.persistence.entity.JobLogDO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface JobLogMapper extends BaseMapper<JobLogDO> {

    @Select("select result from sys_job_log where job_id = #{jobId} order by id desc limit #{limit}")
    List<String> selectRecentResults(@Param("jobId") Long jobId, @Param("limit") int limit);
}
