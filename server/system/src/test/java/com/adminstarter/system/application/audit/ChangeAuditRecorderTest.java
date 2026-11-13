package com.adminstarter.system.application.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.LinkedHashMap;
import java.util.Map;

import com.adminstarter.system.domain.model.ChangeAudit;
import com.adminstarter.system.domain.repository.ChangeAuditRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

/**
 * 变更留痕记录器的三条口径（断言 #109）。
 *
 * <p>这些是"改错就出事"的地方：多记（整行快照）会把差异埋掉；少记（无变化也写）会淹没轨迹；
 * 敏感值漏掩等于把密钥明文存第二份。</p>
 */
class ChangeAuditRecorderTest {

    private final ChangeAuditRepository repository = Mockito.mock(ChangeAuditRepository.class);
    private final ChangeAuditRecorder recorder = new ChangeAuditRecorder(repository, new ObjectMapper());

    private Map<String, Object> map(Object... kv) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            result.put(String.valueOf(kv[i]), kv[i + 1]);
        }
        return result;
    }

    private ChangeAudit captured() {
        ArgumentCaptor<ChangeAudit> captor = ArgumentCaptor.forClass(ChangeAudit.class);
        verify(repository).insert(captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("UPDATE 只记值变化的字段，不把整行快照塞进去")
    void updateRecordsOnlyChangedFields() {
        Map<String, Object> before = map("configValue", "A", "name", "标题", "isEnabled", true);
        Map<String, Object> after = map("configValue", "B", "name", "标题", "isEnabled", true);

        recorder.recordUpdated(ChangeAuditRecorder.TYPE_CONFIG, "sys.name", "系统名称", before, after, false);

        ChangeAudit audit = captured();
        assertThat(audit.getBeforeValue()).contains("configValue").doesNotContain("isEnabled");
        assertThat(audit.getAfterValue()).contains("B").doesNotContain("A");
        assertThat(audit.getAction()).isEqualTo("UPDATE");
        assertThat(audit.getTargetKey()).isEqualTo("sys.name");
    }

    @Test
    @DisplayName("值没变化就不写记录：避免『点一下保存』污染轨迹")
    void unchangedUpdateWritesNothing() {
        Map<String, Object> same = map("configValue", "A", "name", "标题");

        recorder.recordUpdated(ChangeAuditRecorder.TYPE_CONFIG, "sys.name", "系统名称", same, map("configValue", "A", "name", "标题"), false);

        verify(repository, never()).insert(any());
    }

    @Test
    @DisplayName("敏感对象：before 与 after 的值都打码（只掩新值等于把明文搬进旧值）")
    void sensitiveValuesAreMaskedOnBothSides() {
        Map<String, Object> before = map("configValue", "SECRET-OLD");
        Map<String, Object> after = map("configValue", "SECRET-NEW");

        recorder.recordUpdated(ChangeAuditRecorder.TYPE_CONFIG, "secret.key", "密钥", before, after, true);

        ChangeAudit audit = captured();
        assertThat(audit.getBeforeValue()).doesNotContain("SECRET-OLD").contains("******");
        assertThat(audit.getAfterValue()).doesNotContain("SECRET-NEW").contains("******");
        assertThat(audit.getIsSensitive()).isTrue();
    }

    @Test
    @DisplayName("DELETE 只记删除前快照；CREATE 只记新增后快照")
    void deleteAndCreateRecordOneSideOnly() {
        recorder.recordDeleted(ChangeAuditRecorder.TYPE_DICT_TYPE, "user_status", "用户状态", map("type", "user_status"), false);
        ChangeAudit deleted = captured();
        assertThat(deleted.getBeforeValue()).contains("user_status");
        assertThat(deleted.getAfterValue()).isNull();
        assertThat(deleted.getAction()).isEqualTo("DELETE");

        Mockito.clearInvocations(repository);
        recorder.recordCreated(ChangeAuditRecorder.TYPE_DICT_TYPE, "user_status", "用户状态", map("type", "user_status"), false);
        ChangeAudit created = captured();
        assertThat(created.getBeforeValue()).isNull();
        assertThat(created.getAfterValue()).contains("user_status");
        assertThat(created.getAction()).isEqualTo("CREATE");
    }

    @Test
    @DisplayName("留痕失败不阻断业务：仓储抛异常时 recorder 自己吞掉")
    void repositoryFailureDoesNotPropagate() {
        Mockito.doThrow(new IllegalStateException("DB down")).when(repository).insert(any());

        recorder.recordCreated(ChangeAuditRecorder.TYPE_MENU, "/x", "X", map("name", "X"), false);

        // 走到这里没抛异常即为通过：审计写失败不能反过来把业务搞挂
        assertThat(repository).isNotNull();
    }
}
