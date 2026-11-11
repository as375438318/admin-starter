package com.adminstarter.system.domain.repository;

import com.adminstarter.system.domain.model.Menu;

import java.util.Collection;
import java.util.List;

public interface MenuRepository {

    Menu findById(Long id);

    List<Menu> findAll();

    void insert(Menu menu);

    void update(Menu menu);

    void deleteById(Long id);

    boolean existsByParentId(Long parentId);

    List<Menu> findByUserId(Long userId);

    List<String> findPermissionCodesByUserId(Long userId);

    List<Long> findIdsByRoleIds(Collection<Long> roleIds);
}
