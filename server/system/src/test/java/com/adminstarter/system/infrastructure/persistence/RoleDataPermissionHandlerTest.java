package com.adminstarter.system.infrastructure.persistence;

import com.adminstarter.system.application.DataScopeProvider;
import com.adminstarter.system.domain.model.DataScopeCondition;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.expression.Alias;
import net.sf.jsqlparser.schema.Table;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 数据权限拦截器处理器：表登记判定 + 条件拼接 + 公共行例外。
 * <p>三处最危险的行为：未登记表必须**报错**而不是放行；豁免表必须**不追加**条件；
 * 通知的公共行例外必须整体加外层括号，否则 {@code A AND (B) OR (C)} 会让 OR 分支绕过原过滤。</p>
 */
class RoleDataPermissionHandlerTest {

    private final DataScopeProvider provider = mock(DataScopeProvider.class);

    @SuppressWarnings("unchecked")
    private RoleDataPermissionHandler handlerReturning(DataScopeCondition condition) {
        ObjectProvider<DataScopeProvider> objectProvider = mock(ObjectProvider.class);
        when(objectProvider.getObject()).thenReturn(provider);
        when(provider.current()).thenReturn(condition);
        return new RoleDataPermissionHandler(objectProvider);
    }

    private static DataScopeCondition selfOnly() {
        return DataScopeCondition.builder()
                .selfIncluded(true)
                .ownUnrestricted(true)
                .currentUserId(5L)
                .deptIds(List.of())
                .userIds(List.of())
                .build();
    }

    private static Table table(String name, String alias) {
        Table table = new Table(name);
        if (alias != null) {
            table.setAlias(new Alias(alias));
        }
        return table;
    }

    @Test
    @DisplayName("非受控查询（未显式开启）→ 不改写 SQL，避免误伤登录/字典等查询")
    void notControlledReturnsNull() {
        RoleDataPermissionHandler handler = handlerReturning(selfOnly());

        Expression expression = handler.getSqlSegment(table("sys_file", null), null, "any");

        assertNull(expression);
    }

    @Test
    @DisplayName("豁免表 → 不追加条件（用户表豁免，否则管理员看不到自己要管的人）")
    void exemptTableReturnsNull() {
        RoleDataPermissionHandler handler = handlerReturning(selfOnly());

        Expression expression = DataPermissionContext.controlled(
                () -> handler.getSqlSegment(table("sys_user", null), null, "any"));

        assertNull(expression);
    }

    @Test
    @DisplayName("未登记表 → 直接抛错（fail-closed，禁止隐式放行）")
    void unknownTableThrows() {
        RoleDataPermissionHandler handler = handlerReturning(selfOnly());

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> DataPermissionContext.controlled(
                        () -> handler.getSqlSegment(table("t_future_business", null), null, "any")));

        assertTrue(error.getMessage().contains("t_future_business"), "报错须带上表名，便于定位登记缺失");
        assertTrue(error.getMessage().contains("DataPermissionTables"), "报错须指明修复位置");
    }

    @Test
    @DisplayName("受控表但条件为 null（未登录/无用户）→ 不追加")
    void controlledWithoutConditionReturnsNull() {
        RoleDataPermissionHandler handler = handlerReturning(null);

        Expression expression = DataPermissionContext.controlled(
                () -> handler.getSqlSegment(table("sys_file", null), null, "any"));

        assertNull(expression);
    }

    @Test
    @DisplayName("受控表 + 本人范围 → 追加带别名的 create_by 条件")
    void controlledAppliesSelfConditionWithAlias() {
        RoleDataPermissionHandler handler = handlerReturning(selfOnly());

        Expression expression = DataPermissionContext.controlled(
                () -> handler.getSqlSegment(table("sys_file", "f"), null, "any"));

        assertEquals("(f.create_by = 5)", expression.toString());
    }

    @Test
    @DisplayName("表名带 schema 前缀也能识别（避免 admin_starter.sys_file 被当成未登记表）")
    void schemaQualifiedTableIsRecognized() {
        RoleDataPermissionHandler handler = handlerReturning(selfOnly());

        Expression expression = DataPermissionContext.controlled(
                () -> handler.getSqlSegment(table("admin_starter.sys_file", null), null, "any"));

        assertEquals("(create_by = 5)", expression.toString());
    }

    @Test
    @DisplayName("无限制条件（ALL）→ 不追加，且不得被公共行例外收窄成「仅全员通知」")
    void unrestrictedIsNotNarrowedByPublicRow() {
        DataScopeCondition all = DataScopeCondition.builder()
                .unrestricted(true).ownUnrestricted(true).currentUserId(5L)
                .deptIds(List.of()).userIds(List.of()).build();
        RoleDataPermissionHandler handler = handlerReturning(all);

        Expression expression = DataPermissionContext.controlled(
                () -> handler.getSqlSegment(table("sys_notice", "n"), null, "any"));

        assertNull(expression);
    }

    @Test
    @DisplayName("通知：本人范围 + 公共行例外 → 整体括号包裹，OR 分支不绕过原条件")
    void noticeCombinesOwnScopeWithPublicRowInParentheses() {
        RoleDataPermissionHandler handler = handlerReturning(selfOnly());

        Expression expression = DataPermissionContext.controlled(
                () -> handler.getSqlSegment(table("sys_notice", "n"), null, "any"));

        String sql = expression.toString();
        assertTrue(sql.startsWith("(("), "必须以两个左括号开头，形成 (A or B) 的完整包裹：" + sql);
        assertTrue(sql.contains("(n.create_by = 5)"), "应含本人条件：" + sql);
        assertTrue(sql.contains("s.notice_id = n.id"), "公共行例外应使用别名限定外层列：" + sql);
        assertTrue(sql.contains("scope_type = 'ALL'"), "公共行例外的语义是「全员通知」：" + sql);
    }

    @Test
    @DisplayName("非通知表不叠加公共行例外（只有通知有该语义）")
    void otherTablesHaveNoPublicRowException() {
        RoleDataPermissionHandler handler = handlerReturning(selfOnly());

        Expression expression = DataPermissionContext.controlled(
                () -> handler.getSqlSegment(table("sys_file", "f"), null, "any"));

        String sql = expression.toString();
        assertTrue(!sql.contains("sys_notice_scope"), "文件表不应出现通知范围子查询：" + sql);
    }

    @Test
    @DisplayName("生成的片段可被 SQL 解析器接受（防止拼出坏 SQL 直到运行期才炸）")
    void generatedSegmentIsParseable() throws Exception {
        RoleDataPermissionHandler handler = handlerReturning(selfOnly());

        Expression expression = DataPermissionContext.controlled(
                () -> handler.getSqlSegment(table("sys_notice", "n"), null, "any"));

        // 再解析一次：确认拼接结果本身是合法条件表达式
        Expression reparsed = CCJSqlParserUtil.parseCondExpression(expression.toString());
        assertTrue(reparsed.toString().contains("sys_notice_scope"));
    }
}
