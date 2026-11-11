package com.adminstarter.system.infrastructure.persistence.repository;

import com.adminstarter.system.domain.model.Menu;
import com.adminstarter.system.domain.repository.MenuRepository;
import com.adminstarter.system.infrastructure.persistence.converter.MenuConverter;
import com.adminstarter.system.infrastructure.persistence.entity.MenuDO;
import com.adminstarter.system.infrastructure.persistence.mapper.MenuMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class MenuRepositoryImpl implements MenuRepository {

    private final MenuMapper menuMapper;
    private final MenuConverter converter;

    @Override
    public Menu findById(Long id) {
        return converter.toDomain(menuMapper.selectById(id));
    }

    @Override
    public List<Menu> findAll() {
        return menuMapper.selectList(new QueryWrapper<MenuDO>().orderByAsc("sort", "id"))
                .stream().map(converter::toDomain).toList();
    }

    @Override
    public void insert(Menu menu) {
        MenuDO row = converter.toDO(menu);
        menuMapper.insert(row);
        menu.setId(row.getId());
        menu.setCreatedAt(row.getCreatedAt());
        menu.setUpdatedAt(row.getUpdatedAt());
    }

    @Override
    public void update(Menu menu) {
        menuMapper.updateById(converter.toDO(menu));
    }

    @Override
    public void deleteById(Long id) {
        menuMapper.deleteById(id);
    }

    @Override
    public boolean existsByParentId(Long parentId) {
        return menuMapper.selectCount(new QueryWrapper<MenuDO>().eq("parent_id", parentId)) > 0;
    }

    @Override
    public List<Menu> findByUserId(Long userId) {
        return menuMapper.selectMenusByUserId(userId).stream().map(converter::toDomain).toList();
    }

    @Override
    public List<String> findPermissionCodesByUserId(Long userId) {
        return menuMapper.selectPermissionCodesByUserId(userId);
    }

    @Override
    public List<Long> findIdsByRoleIds(Collection<Long> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            return List.of();
        }
        return menuMapper.selectMenuIdsByRoleIds(roleIds);
    }
}
