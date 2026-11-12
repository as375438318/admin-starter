package com.adminstarter.system.application;

import com.adminstarter.common.api.PageQuery;
import com.adminstarter.common.api.PageResponse;
import com.adminstarter.common.api.PageResult;
import com.adminstarter.common.error.BizException;
import com.adminstarter.common.error.ErrorCode;
import com.adminstarter.common.masking.MaskFormat;
import com.adminstarter.common.masking.MaskSelfReadContext;
import com.adminstarter.common.masking.MaskedValueGuard;
import com.adminstarter.common.security.CurrentUser;
import com.adminstarter.common.security.DataScopeContext;
import com.adminstarter.common.util.TimeSupport;
import com.adminstarter.system.application.dto.LogDtos;
import com.adminstarter.system.application.service.PasswordPolicy;
import com.adminstarter.system.application.dto.ProfileDtos;
import com.adminstarter.system.domain.model.LoginLog;
import com.adminstarter.system.domain.model.User;
import com.adminstarter.system.domain.model.UserSession;
import com.adminstarter.system.domain.repository.DeptRepository;
import com.adminstarter.system.domain.repository.LoginLogRepository;
import com.adminstarter.system.domain.repository.MenuRepository;
import com.adminstarter.system.domain.repository.RoleRepository;
import com.adminstarter.system.domain.repository.UserRepository;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProfileAppService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final DeptRepository deptRepository;
    private final MenuRepository menuRepository;
    private final LoginLogRepository loginLogRepository;
    private final UserSessionAppService sessionAppService;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;

    public ProfileDtos.Response profile() {
        CurrentUser current = requireCurrent();
        MaskSelfReadContext.enable();
        User user = userRepository.findById(current.getUserId());
        if (user == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "账号不存在或已停用");
        }
        ProfileDtos.Response response = new ProfileDtos.Response();
        response.setUserId(user.getId());
        response.setUsername(user.getUsername());
        response.setNickname(user.getNickname());
        response.setEmail(user.getEmail());
        response.setPhone(user.getPhone());
        response.setDeptId(user.getDeptId());
        if (user.getDeptId() != null && deptRepository.findById(user.getDeptId()) != null) {
            response.setDeptName(deptRepository.findById(user.getDeptId()).getName());
        }
        response.setRoles(userRepository.findRoleCodesByUserId(user.getId()));
        response.setPermissions(menuRepository.findPermissionCodesByUserId(user.getId()));
        return response;
    }

    @Transactional
    public ProfileDtos.Response updateProfile(ProfileDtos.UpdateRequest request) {
        CurrentUser current = requireCurrent();
        User user = userRepository.findById(current.getUserId());
        if (user == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "账号不存在或已停用");
        }
        user.setNickname(request.getNickname());
        MaskedValueGuard.rejectMaskedValue("email", request.getEmail());
        MaskedValueGuard.rejectMaskedValue("phone", request.getPhone());
        user.setEmail(MaskedValueGuard.apply("email", "user:email", MaskFormat.EMAIL,
                user.getEmail(), request.getEmail(), true, true));
        user.setPhone(MaskedValueGuard.apply("phone", "user:phone", MaskFormat.PHONE,
                user.getPhone(), request.getPhone(), true, true));
        userRepository.update(user);
        return profile();
    }

    @Transactional
    public Long changePassword(ProfileDtos.ChangePasswordRequest request) {
        CurrentUser current = requireCurrent();
        User user = userRepository.findById(current.getUserId());
        if (user == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "账号不存在或已停用");
        }
        if (!passwordEncoder.matches(request.getOldPassword(), user.getPassword())) {
            throw new BizException(ErrorCode.VALIDATION_FAILED, "原密码不正确");
        }
        // 等保 P0-1：新口令必须过复杂度策略，且不得与旧口令相同
        passwordPolicy.validate(request.getNewPassword(), user.getUsername());
        if (passwordEncoder.matches(request.getNewPassword(), user.getPassword())) {
            throw new BizException(ErrorCode.PASSWORD_TOO_WEAK, "新密码不能与原密码相同");
        }
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        user.setPasswordUpdatedAt(LocalDateTime.now());
        user.setMustChangePassword(false);
        userRepository.update(user);
        userRepository.bumpTokenVersion(user.getId(), "PASSWORD_CHANGED");
        // 账号级失效由 token_version 负责；会话表同批标记其他设备，当前会话保留（外层随后重签）
        sessionAppService.invalidateUserExcept(user.getId(), UserSession.REASON_PASSWORD_CHANGED,
                current.getSessionId());
        return user.getId();
    }

    public PageResponse<LogDtos.LoginLogResponse> loginLogs(PageQuery pageQuery) {
        CurrentUser current = requireCurrent();
        int page = pageQuery.resolvePage();
        int pageSize = queryPageSize(pageQuery);
        PageResult<LoginLog> result = loginLogRepository.pageByUser(current.getUserId(), page, pageSize);
        return PageResponse.of(page, pageSize, result.getList().stream().map(this::toResponse).toList(),
                result.getTotal());
    }

    private int queryPageSize(PageQuery pageQuery) {
        return pageQuery.resolvePageSize();
    }

    private CurrentUser requireCurrent() {
        CurrentUser current = DataScopeContext.get();
        if (current == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "未登录");
        }
        return current;
    }

    private LogDtos.LoginLogResponse toResponse(LoginLog log) {
        LogDtos.LoginLogResponse response = new LogDtos.LoginLogResponse();
        response.setId(log.getId());
        response.setUserId(log.getUserId());
        response.setUsername(log.getUsername());
        response.setIp(log.getIp());
        response.setUserAgent(log.getUserAgent());
        response.setIsSuccess(log.getIsSuccess());
        response.setMessage(log.getMessage());
        response.setCreatedAt(TimeSupport.toInstant(log.getCreatedAt()));
        return response;
    }
}
