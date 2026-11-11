package com.adminstarter.system.application;

import com.adminstarter.common.error.BizException;
import com.adminstarter.common.error.ErrorCode;
import com.adminstarter.common.util.TimeSupport;
import com.adminstarter.system.application.dto.MenuDtos;
import com.adminstarter.system.domain.enums.MenuType;
import com.adminstarter.system.domain.model.Menu;
import com.adminstarter.system.domain.repository.MenuRepository;
import com.adminstarter.system.domain.repository.RoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class MenuAppService {

    private final MenuRepository menuRepository;
    private final RoleRepository roleRepository;

    public List<MenuDtos.Response> tree() {
        List<Menu> menus = menuRepository.findAll();
        return buildTree(menus, 0L);
    }

    /**
     * 当前用户可见路由（不含按钮）。
     */
    public List<MenuDtos.Route> routes(Long userId) {
        List<Menu> menus = menuRepository.findByUserId(userId).stream()
                .filter(menu -> menu.getMenuType() != MenuType.BUTTON)
                .filter(menu -> Boolean.TRUE.equals(menu.getIsEnabled()))
                .toList();
        return buildRoutes(menus, 0L);
    }

    public List<String> permissions(Long userId) {
        return menuRepository.findPermissionCodesByUserId(userId);
    }

    @Transactional
    public MenuDtos.Response create(MenuDtos.SaveRequest request) {
        validateParent(request.getParentId(), null);
        Menu menu = new Menu();
        apply(menu, request);
        menuRepository.insert(menu);
        return toResponse(menu);
    }

    @Transactional
    public MenuDtos.Response update(Long id, MenuDtos.SaveRequest request) {
        Menu menu = menuRepository.findById(id);
        if (menu == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "菜单不存在");
        }
        validateParent(request.getParentId(), id);
        apply(menu, request);
        menuRepository.update(menu);
        return toResponse(menu);
    }

    @Transactional
    public void delete(Long id) {
        Menu menu = menuRepository.findById(id);
        if (menu == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "菜单不存在");
        }
        if (menuRepository.existsByParentId(id)) {
            throw new BizException(ErrorCode.STATE_CONFLICT, "存在子菜单，不能删除");
        }
        if (roleRepository.countByMenuId(id) > 0) {
            throw new BizException(ErrorCode.STATE_CONFLICT, "菜单已分配给角色，不能删除");
        }
        menuRepository.deleteById(id);
    }

    private void validateParent(Long parentId, Long selfId) {
        if (parentId == null || parentId == 0L) {
            return;
        }
        if (selfId != null && parentId.equals(selfId)) {
            throw new BizException(ErrorCode.INVALID_PARAMETER, "上级菜单不能是自身");
        }
        if (menuRepository.findById(parentId) == null) {
            throw new BizException(ErrorCode.INVALID_PARAMETER, "上级菜单不存在");
        }
    }

    private void apply(Menu menu, MenuDtos.SaveRequest request) {
        menu.setParentId(request.getParentId() == null ? 0L : request.getParentId());
        menu.setName(request.getName());
        menu.setMenuType(MenuType.fromCode(request.getMenuType()));
        menu.setPath(request.getPath());
        menu.setComponent(request.getComponent());
        menu.setPermissionCode(request.getPermissionCode());
        menu.setIcon(request.getIcon());
        menu.setSort(request.getSort() == null ? 0 : request.getSort());
        menu.setIsVisible(request.getIsVisible() == null || request.getIsVisible());
        menu.setIsEnabled(request.getIsEnabled() == null || request.getIsEnabled());
    }

    private List<MenuDtos.Response> buildTree(List<Menu> menus, Long parentId) {
        Map<Long, List<Menu>> groups = new LinkedHashMap<>();
        for (Menu menu : menus) {
            groups.computeIfAbsent(menu.getParentId() == null ? 0L : menu.getParentId(),
                    key -> new ArrayList<>()).add(menu);
        }
        List<Menu> children = groups.getOrDefault(parentId, List.of());
        List<MenuDtos.Response> result = new ArrayList<>();
        for (Menu menu : children) {
            MenuDtos.Response response = toResponse(menu);
            List<MenuDtos.Response> childTree = buildTree(menus, menu.getId());
            response.setChildren(childTree.isEmpty() ? null : childTree);
            result.add(response);
        }
        return result;
    }

    private List<MenuDtos.Route> buildRoutes(List<Menu> menus, Long parentId) {
        Map<Long, List<Menu>> groups = new HashMap<>();
        for (Menu menu : menus) {
            groups.computeIfAbsent(menu.getParentId() == null ? 0L : menu.getParentId(),
                    key -> new ArrayList<>()).add(menu);
        }
        List<Menu> children = groups.getOrDefault(parentId, List.of());
        List<MenuDtos.Route> result = new ArrayList<>();
        for (Menu menu : children) {
            MenuDtos.Route route = new MenuDtos.Route();
            route.setId(menu.getId());
            route.setPath(menu.getPath());
            route.setName("Menu" + menu.getId());
            route.setComponent(menu.getComponent());
            MenuDtos.RouteMeta meta = new MenuDtos.RouteMeta();
            meta.setTitle(menu.getName());
            meta.setIcon(menu.getIcon());
            meta.setHidden(!Boolean.TRUE.equals(menu.getIsVisible()));
            meta.setPermission(menu.getPermissionCode());
            route.setMeta(meta);
            List<MenuDtos.Route> childRoutes = buildRoutes(menus, menu.getId());
            route.setChildren(childRoutes.isEmpty() ? null : childRoutes);
            result.add(route);
        }
        return result;
    }

    private MenuDtos.Response toResponse(Menu menu) {
        MenuDtos.Response response = new MenuDtos.Response();
        response.setId(menu.getId());
        response.setParentId(menu.getParentId());
        response.setName(menu.getName());
        response.setMenuType(menu.getMenuType() == null ? null : menu.getMenuType().getCode());
        response.setPath(menu.getPath());
        response.setComponent(menu.getComponent());
        response.setPermissionCode(menu.getPermissionCode());
        response.setIcon(menu.getIcon());
        response.setSort(menu.getSort());
        response.setIsVisible(menu.getIsVisible());
        response.setIsEnabled(menu.getIsEnabled());
        response.setCreatedAt(TimeSupport.toInstant(menu.getCreatedAt()));
        response.setUpdatedAt(TimeSupport.toInstant(menu.getUpdatedAt()));
        return response;
    }
}
