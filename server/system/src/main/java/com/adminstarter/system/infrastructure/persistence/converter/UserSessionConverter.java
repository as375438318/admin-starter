package com.adminstarter.system.infrastructure.persistence.converter;

import com.adminstarter.system.domain.model.UserSession;
import com.adminstarter.system.infrastructure.persistence.entity.UserSessionDO;
import org.springframework.stereotype.Component;

@Component
public class UserSessionConverter {

    public UserSessionDO toDO(UserSession session) {
        UserSessionDO row = new UserSessionDO();
        row.setId(session.getId());
        row.setSessionId(session.getSessionId());
        row.setUserId(session.getUserId());
        row.setUsername(session.getUsername());
        row.setLoginAt(session.getLoginAt());
        row.setLastActiveAt(session.getLastActiveAt());
        row.setIp(session.getIp());
        row.setUserAgent(session.getUserAgent());
        row.setDeviceType(session.getDeviceType());
        row.setBrowser(session.getBrowser());
        row.setOs(session.getOs());
        row.setInvalidatedAt(session.getInvalidatedAt());
        row.setInvalidateReason(session.getInvalidateReason());
        row.setCreatedAt(session.getCreatedAt());
        row.setUpdatedAt(session.getUpdatedAt());
        row.setNickname(session.getNickname());
        return row;
    }

    public UserSession toDomain(UserSessionDO row) {
        if (row == null) {
            return null;
        }
        UserSession session = new UserSession();
        session.setId(row.getId());
        session.setSessionId(row.getSessionId());
        session.setUserId(row.getUserId());
        session.setUsername(row.getUsername());
        session.setLoginAt(row.getLoginAt());
        session.setLastActiveAt(row.getLastActiveAt());
        session.setIp(row.getIp());
        session.setUserAgent(row.getUserAgent());
        session.setDeviceType(row.getDeviceType());
        session.setBrowser(row.getBrowser());
        session.setOs(row.getOs());
        session.setInvalidatedAt(row.getInvalidatedAt());
        session.setInvalidateReason(row.getInvalidateReason());
        session.setCreatedAt(row.getCreatedAt());
        session.setUpdatedAt(row.getUpdatedAt());
        session.setNickname(row.getNickname());
        return session;
    }
}
