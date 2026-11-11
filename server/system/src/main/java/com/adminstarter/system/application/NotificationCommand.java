package com.adminstarter.system.application;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 通知发布命令：模块间调用的入参，不依赖 HTTP 层。
 */
@Data
@Builder
public class NotificationCommand {

    private String title;
    private String content;
    private String level;
    private String type;
    private Boolean revocable;
    private List<Scope> scopes;
    /** 发布人；系统调用可为空 */
    private Long publisherId;
    /** 发布人部门；系统调用可为空 */
    private Long publisherDeptId;

    @Data
    @Builder
    public static class Scope {
        /** ALL / ROLE / DEPT / USER */
        private String scopeType;
        private Long targetId;

        public static Scope all() {
            return Scope.builder().scopeType("ALL").build();
        }

        public static Scope ofRole(Long roleId) {
            return Scope.builder().scopeType("ROLE").targetId(roleId).build();
        }

        public static Scope ofDept(Long deptId) {
            return Scope.builder().scopeType("DEPT").targetId(deptId).build();
        }

        public static Scope ofUser(Long userId) {
            return Scope.builder().scopeType("USER").targetId(userId).build();
        }
    }
}
