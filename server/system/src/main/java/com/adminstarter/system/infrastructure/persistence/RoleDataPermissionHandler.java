package com.adminstarter.system.infrastructure.persistence;

import com.adminstarter.system.application.DataScopeProvider;
import com.adminstarter.system.domain.model.DataScopeCondition;
import com.baomidou.mybatisplus.extension.plugins.handler.MultiDataPermissionHandler;
import lombok.RequiredArgsConstructor;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Table;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * MyBatis 数据权限拦截器处理器：给受控表的 SELECT 追加 where 条件。
 * <p>表必须显式登记（{@link DataPermissionTables}）：受控表按范围过滤，豁免表明式放行，
 * 未登记表在受控查询中直接报错，避免“漏配即放行”的隐式行为。</p>
 * <p>用 ObjectProvider 延迟获取，避免拦截器 -> 仓储 -> SqlSessionFactory 的构造期循环依赖。</p>
 */
@Component
@RequiredArgsConstructor
public class RoleDataPermissionHandler implements MultiDataPermissionHandler {

    private final ObjectProvider<DataScopeProvider> dataScopeProvider;

    @Override
    public Expression getSqlSegment(Table table, Expression where, String mappedStatementId) {
        if (!DataPermissionContext.isControlled()) {
            return null;
        }
        String tableName = table.getName();
        if (tableName != null && tableName.contains(".")) {
            tableName = tableName.substring(tableName.lastIndexOf('.') + 1);
        }
        DataPermissionTables.TableKind kind = DataPermissionTables.kindOf(tableName);
        if (kind == DataPermissionTables.TableKind.EXEMPT) {
            return null;
        }
        if (kind == DataPermissionTables.TableKind.UNKNOWN) {
            throw new IllegalStateException("数据权限查询涉及未登记表: " + tableName
                    + "；请在 DataPermissionTables 中显式登记为 CONTROLLED 或 EXEMPT");
        }
        String ownerColumn = DataPermissionTables.ownerColumn(tableName);
        DataScopeCondition condition = dataScopeProvider.getObject().current();
        if (condition == null) {
            return null;
        }
        String alias = table.getAlias() == null ? null : table.getAlias().getName();
        String sql = DataScopeSqlBuilder.build(alias, condition, ownerColumn, DataPermissionTables.selfColumn(tableName));
        String publicSql = DataPermissionTables.publicRowCondition(tableName, alias);
        // sql == null 表示数据权限不设限；此时不能再叠加公共行条件，否则会把无限制角色收窄成“仅全员通知”。
        if (sql != null && publicSql != null) {
            // 整体必须再包一层括号：MyBatis-Plus 以 AND 追加，缺外层括号会变成 A AND (B) OR (C)，OR 分支绕过原过滤条件。
            sql = "((" + sql + ") or (" + publicSql + "))";
        }
        if (sql == null) {
            return null;
        }
        try {
            return CCJSqlParserUtil.parseCondExpression(sql);
        } catch (JSQLParserException e) {
            throw new IllegalStateException("数据权限条件解析失败: " + sql, e);
        }
    }
}
