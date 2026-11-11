package com.adminstarter.system.infrastructure.persistence.repository;

import com.adminstarter.system.domain.model.Dept;
import com.adminstarter.system.domain.repository.DeptRepository;
import com.adminstarter.system.infrastructure.persistence.converter.DeptConverter;
import com.adminstarter.system.infrastructure.persistence.entity.DeptDO;
import com.adminstarter.system.infrastructure.persistence.mapper.DeptMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class DeptRepositoryImpl implements DeptRepository {

    private final DeptMapper deptMapper;
    private final DeptConverter converter;

    @Override
    public Dept findById(Long id) {
        return converter.toDomain(deptMapper.selectById(id));
    }

    @Override
    public List<Dept> findAll() {
        return deptMapper.selectList(new QueryWrapper<DeptDO>().orderByAsc("sort", "id"))
                .stream().map(converter::toDomain).toList();
    }

    @Override
    public List<Dept> findByIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return deptMapper.selectBatchIds(ids).stream().map(converter::toDomain).toList();
    }

    @Override
    public void insert(Dept dept) {
        DeptDO row = converter.toDO(dept);
        deptMapper.insert(row);
        dept.setId(row.getId());
        dept.setCreatedAt(row.getCreatedAt());
        dept.setUpdatedAt(row.getUpdatedAt());
    }

    @Override
    public void update(Dept dept) {
        deptMapper.updateById(converter.toDO(dept));
    }

    @Override
    public void deleteById(Long id) {
        deptMapper.deleteById(id);
    }

    @Override
    public boolean existsByParentId(Long parentId) {
        return deptMapper.selectCount(new QueryWrapper<DeptDO>().eq("parent_id", parentId)) > 0;
    }

    @Override
    public List<Long> findDescendantIds(Long deptId) {
        return deptMapper.selectDescendantIds(deptId);
    }
}
