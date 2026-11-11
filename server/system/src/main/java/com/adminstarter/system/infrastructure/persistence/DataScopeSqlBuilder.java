package com.adminstarter.system.infrastructure.persistence;

import com.adminstarter.system.domain.model.DataScopeCondition;

import java.util.ArrayList;
import java.util.List;

/**
 * 把 DataScopeCondition 编译成 SQL 条件片段；只拼接数据库内的数值 ID，不接受外部字符串。
 */
public final class DataScopeSqlBuilder {

    private DataScopeSqlBuilder() {
    }

    public static String build(String tableAlias, DataScopeCondition condition, String ownerColumn, String selfColumn) {
        if (condition == null) {
            return null;
        }
        String prefix = tableAlias == null || tableAlias.isBlank() ? "" : tableAlias + ".";
        String userColumn = selfColumn == null || selfColumn.isBlank() ? "create_by" : selfColumn;
        Long userId = condition.getCurrentUserId();

        String orgCondition = null;
        if (!condition.isUnrestricted()) {
            List<String> parts = new ArrayList<>();
            if (condition.getDeptIds() != null && !condition.getDeptIds().isEmpty()) {
                parts.add(prefix + "dept_id in (" + joinIds(condition.getDeptIds()) + ")");
            }
            if (condition.isSelfIncluded() && userId != null) {
                parts.add(prefix + userColumn + " = " + userId);
            }
            if (condition.getUserIds() != null && !condition.getUserIds().isEmpty()) {
                parts.add(prefix + userColumn + " in (" + joinIds(condition.getUserIds()) + ")");
            }
            if (parts.isEmpty()) {
                return "1 = 0";
            }
            orgCondition = "(" + String.join(" or ", parts) + ")";
        }

        String ownCondition = null;
        if (!condition.isOwnUnrestricted()) {
            List<String> parts = new ArrayList<>();
            if (condition.isOwnCreator() && userId != null) {
                parts.add(prefix + "create_by = " + userId);
            }
            if (condition.isOwnOwner() && userId != null) {
                parts.add(prefix + ownerColumn + " = " + userId);
            }
            if (parts.isEmpty()) {
                return "1 = 0";
            }
            ownCondition = "(" + String.join(" or ", parts) + ")";
        }

        if (orgCondition == null && ownCondition == null) {
            return null;
        }
        if (orgCondition == null) {
            return ownCondition;
        }
        if (ownCondition == null) {
            return orgCondition;
        }
        return orgCondition + " and " + ownCondition;
    }

    private static String joinIds(List<Long> ids) {
        return String.join(",", ids.stream().map(String::valueOf).toList());
    }
}
