package com.adminstarter.system.application;

import com.adminstarter.system.domain.model.LoginLog;
import com.adminstarter.system.domain.model.User;
import com.adminstarter.system.domain.repository.LoginLogRepository;
import com.adminstarter.system.domain.repository.MenuRepository;
import com.adminstarter.system.domain.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 提供给认证模块的最小用户查询端口，避免 auth 直接依赖 system 的领域实现。
 */
@Service
@RequiredArgsConstructor
public class AuthUserQueryService {

    private final UserRepository userRepository;
    private final MenuRepository menuRepository;
    private final LoginLogRepository loginLogRepository;

    public User findByUsername(String username) {
        return userRepository.findByUsername(username);
    }

    public User findById(Long userId) {
        return userRepository.findById(userId);
    }

    public List<String> roleCodes(Long userId) {
        return userRepository.findRoleCodesByUserId(userId);
    }

    public List<String> permissionCodes(Long userId) {
        return menuRepository.findPermissionCodesByUserId(userId);
    }

    public void recordLogin(LoginLog loginLog) {
        loginLogRepository.insert(loginLog);
    }
}
