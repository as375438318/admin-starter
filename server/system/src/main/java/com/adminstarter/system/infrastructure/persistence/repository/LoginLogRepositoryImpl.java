package com.adminstarter.system.infrastructure.persistence.repository;

import com.adminstarter.common.api.PageResult;
import com.adminstarter.system.domain.model.LoginLog;
import com.adminstarter.system.domain.repository.LoginLogRepository;
import com.adminstarter.system.infrastructure.persistence.converter.LogConverter;
import com.adminstarter.system.infrastructure.persistence.entity.LoginLogDO;
import com.adminstarter.system.infrastructure.persistence.mapper.LoginLogMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class LoginLogRepositoryImpl implements LoginLogRepository {

    private final LoginLogMapper loginLogMapper;
    private final LogConverter converter;

    @Override
    public void insert(LoginLog loginLog) {
        LoginLogDO row = converter.toDO(loginLog);
        loginLogMapper.insert(row);
        loginLog.setId(row.getId());
        loginLog.setCreatedAt(row.getCreatedAt());
    }

    @Override
    public PageResult<LoginLog> pageByUser(Long userId, int page, int pageSize) {
        Page<LoginLogDO> result = loginLogMapper.selectPage(new Page<>(page, pageSize),
                new QueryWrapper<LoginLogDO>().eq("user_id", userId).orderByDesc("created_at", "id"));
        return PageResult.of(result.getRecords().stream().map(converter::toDomain).toList(), result.getTotal());
    }
}
