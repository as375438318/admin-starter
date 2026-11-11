package com.adminstarter.system.application.excel;

import com.adminstarter.common.api.ImportErrorData;
import com.adminstarter.common.api.ValidationErrorData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 导入错误汇总：维护「出错行数」与「字段级错误条数」两个不同口径，并对返回清单做 200 条截断。
 * <p>这三个数字前端会分别展示（“共 N 行有问题 / 共 M 处错误 / 还有 K 处未显示”），
 * 混口径或截断算错会让用户以为已经改完。</p>
 */
class ImportErrorCollectorTest {

    @Test
    @DisplayName("初始无错误")
    void startsEmpty() {
        ImportErrorCollector collector = new ImportErrorCollector();

        assertFalse(collector.hasErrors());
        assertEquals(0, collector.errorCount());
        assertEquals(0, collector.toData(0).getErrors().size());
    }

    @Test
    @DisplayName("错误信息带上真实行号与表头：第 N 行 · 表头")
    void errorCarriesRowNumberAndHeader() {
        ImportErrorCollector collector = new ImportErrorCollector();
        collector.add(7, "任务标题", "REQUIRED", "不能为空");

        ValidationErrorData.FieldError error = collector.toData(10).getErrors().get(0);
        assertEquals("第 7 行 · 任务标题", error.getField());
        assertEquals("REQUIRED", error.getCode());
        assertEquals("不能为空", error.getMessage());
    }

    @Test
    @DisplayName("同一行多个字段出错：行数按行去重、条数按字段累计")
    void rowCountAndErrorCountAreDistinct() {
        ImportErrorCollector collector = new ImportErrorCollector();
        collector.add(3, "任务标题", "REQUIRED", "不能为空");
        collector.add(3, "级别", "INVALID_FORMAT", "只能是 普通(NORMAL)");
        collector.add(4, "负责人", "REF_NOT_FOUND", "负责人不存在");

        ImportErrorData data = collector.toData(100);
        assertEquals(3, data.getErrorCount(), "字段级错误 3 处");
        assertEquals(2, data.getErrorRows(), "出错行 2 行（第 3、4 行）");
        assertEquals(100, data.getTotalRows());
        assertEquals(0, data.getRemainingErrors());
    }

    @Test
    @DisplayName("超过 200 条只返回前 200 条，剩余条数单独给出（不能静默截断）")
    void errorsAreCappedButCounted() {
        ImportErrorCollector collector = new ImportErrorCollector();
        for (int index = 1; index <= 250; index++) {
            collector.add(index, "任务标题", "REQUIRED", "不能为空");
        }

        ImportErrorData data = collector.toData(300);
        assertEquals(ImportErrorCollector.MAX_RETURNED, data.getErrors().size());
        assertEquals(250, data.getErrorCount());
        assertEquals(250, data.getErrorRows());
        assertEquals(50, data.getRemainingErrors(), "未显示的 50 条必须报出来");
    }

    @Test
    @DisplayName("返回的是快照副本：之后继续 add 不影响已取出的 data")
    void toDataReturnsSnapshot() {
        ImportErrorCollector collector = new ImportErrorCollector();
        collector.add(1, "标题", "REQUIRED", "不能为空");
        ImportErrorData snapshot = collector.toData(5);

        collector.add(2, "标题", "REQUIRED", "不能为空");

        assertEquals(1, snapshot.getErrors().size());
        assertEquals(1, snapshot.getErrorCount());
        assertEquals(2, collector.errorCount());
        assertTrue(collector.hasErrors());
        assertTrue(List.copyOf(snapshot.getErrors()) instanceof List);
    }
}
