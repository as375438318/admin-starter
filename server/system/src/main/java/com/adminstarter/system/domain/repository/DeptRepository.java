package com.adminstarter.system.domain.repository;

import com.adminstarter.system.domain.model.Dept;

import java.util.Collection;
import java.util.List;

public interface DeptRepository {

    Dept findById(Long id);

    List<Dept> findAll();

    List<Dept> findByIds(Collection<Long> ids);

    void insert(Dept dept);

    void update(Dept dept);

    void deleteById(Long id);

    boolean existsByParentId(Long parentId);

    List<Long> findDescendantIds(Long deptId);
}
