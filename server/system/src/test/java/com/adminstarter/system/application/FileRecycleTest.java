package com.adminstarter.system.application;

import com.adminstarter.common.security.CurrentUser;
import com.adminstarter.common.security.DataScopeContext;
import com.adminstarter.system.domain.model.FileRecord;
import com.adminstarter.system.domain.repository.FileChunkUploadRepository;
import com.adminstarter.system.domain.repository.FileRepository;
import com.adminstarter.system.domain.storage.FileStorage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 文件回收站：删除进回收站 / 恢复 / 彻底删除。
 * <p>三条最容易出事的规则：</p>
 * <ol>
 *   <li><b>重复操作必须幂等</b>（连点两次删除、或删除别人已删的文件，都不能报错）；</li>
 *   <li><b>记录不可见时不得暴露存在性</b>（删除/恢复对"不存在"与"无权"表现一致）；</li>
 *   <li><b>彻底删除先删行、再按 storage_path 计数决定是否删物理字节</b>——文件按 SHA-256 去重共享，
 *       计数不为 0 就删字节，会把别人的文件变成空壳。</li>
 * </ol>
 */
class FileRecycleTest {

    private final FileRepository fileRepository = mock(FileRepository.class);
    private final FileChunkUploadRepository chunkUploadRepository = mock(FileChunkUploadRepository.class);
    private final PublicConfigAppService publicConfigAppService = mock(PublicConfigAppService.class);
    private final FileStorage fileStorage = mock(FileStorage.class);

    private final FileAppService service = new FileAppService(
            fileRepository, chunkUploadRepository, publicConfigAppService, fileStorage);

    private static final String PATH = "2026/11/11/abc123.bin";

    private static FileRecord record() {
        FileRecord record = new FileRecord();
        record.setId(42L);
        record.setOriginalName("测试.pdf");
        record.setStoragePath(PATH);
        return record;
    }

    private static void login(long userId) {
        DataScopeContext.set(CurrentUser.builder().userId(userId).username("tester")
                .permissions(Set.of()).build());
    }

    @AfterEach
    void tearDown() {
        DataScopeContext.clear();
    }

    // ---------------------------------------------------------------- 删除进回收站

    @Test
    @DisplayName("删除：记录不可见/不存在 → 静默成功（幂等，不暴露存在性）")
    void deleteMissingRecordIsIdempotent() {
        login(7L);
        when(fileRepository.findById(42L)).thenReturn(null);

        service.delete(42L);

        verify(fileRepository, never()).moveToRecycle(anyLong(), anyLong());
        verifyNoInteractions(fileStorage);
    }

    @Test
    @DisplayName("删除：可见记录 → 移入回收站并记录操作人")
    void deleteMovesToRecycleWithOperator() {
        login(7L);
        when(fileRepository.findById(42L)).thenReturn(record());

        service.delete(42L);

        verify(fileRepository).moveToRecycle(42L, 7L);
        verify(fileStorage, never()).delete(anyString());
    }

    @Test
    @DisplayName("删除：无登录上下文也不 NPE（系统任务可能调用）")
    void deleteWithoutContextDoesNotThrow() {
        when(fileRepository.findById(42L)).thenReturn(record());

        service.delete(42L);

        verify(fileRepository).moveToRecycle(42L, null);
    }

    // ---------------------------------------------------------------- 恢复

    @Test
    @DisplayName("恢复：不在回收站 → 静默成功（幂等）")
    void restoreMissingIsIdempotent() {
        login(7L);
        when(fileRepository.findDeletedById(42L)).thenReturn(null);

        service.restore(42L);

        verify(fileRepository, never()).restore(anyLong());
    }

    @Test
    @DisplayName("恢复：在回收站 → 恢复记录，不动物理文件")
    void restoreBringsRecordBack() {
        login(7L);
        when(fileRepository.findDeletedById(42L)).thenReturn(record());

        service.restore(42L);

        verify(fileRepository).restore(42L);
        verifyNoInteractions(fileStorage);
    }

    // ---------------------------------------------------------------- 彻底删除

    @Test
    @DisplayName("彻底删除：记录不存在/不在回收站 → 静默成功，且不碰物理文件")
    void purgeMissingIsIdempotent() {
        login(7L);
        when(fileRepository.findDeletedById(42L)).thenReturn(null);

        service.purge(42L);

        verify(fileRepository, never()).purge(anyLong());
        verifyNoInteractions(fileStorage);
    }

    @Test
    @DisplayName("彻底删除：仍有其它记录引用同一 storage_path（去重共享）→ 保留物理字节")
    void purgeKeepsSharedBytes() {
        login(7L);
        when(fileRepository.findDeletedById(42L)).thenReturn(record());
        when(fileRepository.countByStoragePath(PATH)).thenReturn(2L);

        service.purge(42L);

        verify(fileRepository).purge(42L);
        verify(fileStorage, never()).delete(anyString());
    }

    @Test
    @DisplayName("彻底删除：无其它引用 → 删物理字节")
    void purgeDeletesBytesWhenUnreferenced() {
        login(7L);
        when(fileRepository.findDeletedById(42L)).thenReturn(record());
        when(fileRepository.countByStoragePath(PATH)).thenReturn(0L);

        service.purge(42L);

        verify(fileRepository).purge(42L);
        verify(fileStorage).delete(PATH);
    }

    @Test
    @DisplayName("彻底删除顺序：先删行、再计数、最后删字节（顺序反了会把仍被引用的字节删掉）")
    void purgeOrderIsRowThenCountThenBytes() {
        login(7L);
        when(fileRepository.findDeletedById(42L)).thenReturn(record());
        when(fileRepository.countByStoragePath(PATH)).thenReturn(0L);

        service.purge(42L);

        InOrder order = inOrder(fileRepository, fileStorage);
        order.verify(fileRepository).purge(42L);
        order.verify(fileRepository).countByStoragePath(PATH);
        order.verify(fileStorage).delete(PATH);
    }

    @Test
    @DisplayName("彻底删除只按被删记录的 storage_path 判断，不用整批或通配删除")
    void purgeUsesExactStoragePath() {
        login(7L);
        when(fileRepository.findDeletedById(42L)).thenReturn(record());
        when(fileRepository.countByStoragePath(PATH)).thenReturn(0L);

        service.purge(42L);

        verify(fileStorage, times(1)).delete(PATH);
        verify(fileStorage, never()).deleteRecursively(anyString());
    }
}
