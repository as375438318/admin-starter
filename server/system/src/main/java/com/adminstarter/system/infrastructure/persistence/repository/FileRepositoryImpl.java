package com.adminstarter.system.infrastructure.persistence.repository;

import com.adminstarter.common.api.PageResult;
import com.adminstarter.system.domain.model.FileRecord;
import com.adminstarter.system.domain.repository.FileRepository;
import com.adminstarter.system.infrastructure.persistence.converter.FileConverter;
import com.adminstarter.system.infrastructure.persistence.entity.FileDO;
import com.adminstarter.system.infrastructure.persistence.mapper.FileMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class FileRepositoryImpl implements FileRepository {

    private final FileMapper fileMapper;
    private final FileConverter converter;

    @Override
    public void insert(FileRecord record) {
        FileDO row = converter.toDO(record);
        fileMapper.insert(row);
        record.setId(row.getId());
        record.setCreatedAt(row.getCreatedAt());
    }

    @Override
    public FileRecord findById(Long id) {
        return converter.toDomain(fileMapper.selectById(id));
    }

    @Override
    public FileRecord findByShaAndUploader(String sha256, long size, Long uploaderId) {
        return converter.toDomain(fileMapper.selectByShaAndUploader(sha256, size, uploaderId));
    }

    @Override
    public FileRecord findBySha(String sha256, long size) {
        return converter.toDomain(fileMapper.selectBySha(sha256, size));
    }

    @Override
    public void moveToRecycle(Long id, Long userId) {
        fileMapper.moveToRecycle(id, userId);
    }

    @Override
    public FileRecord findDeletedById(Long id) {
        return converter.toDomain(fileMapper.selectDeletedById(id));
    }

    @Override
    public PageResult<FileRecord> page(String keyword, int page, int pageSize) {
        QueryWrapper<FileDO> wrapper = new QueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            wrapper.like("original_name", keyword);
        }
        wrapper.orderByDesc("created_at", "id");
        Page<FileDO> result = fileMapper.selectPage(new Page<>(page, pageSize), wrapper);
        return PageResult.of(result.getRecords().stream().map(converter::toDomain).toList(), result.getTotal());
    }

    @Override
    public PageResult<FileRecord> pageDeleted(String keyword, int page, int pageSize) {
        Page<FileDO> result = fileMapper.selectDeletedPage(new Page<>(page, pageSize), keyword);
        return PageResult.of(result.getRecords().stream().map(converter::toDomain).toList(), result.getTotal());
    }

    @Override
    public boolean restore(Long id) {
        return fileMapper.restore(id) > 0;
    }

    @Override
    public boolean purge(Long id) {
        return fileMapper.purge(id) > 0;
    }

    @Override
    public long countByStoragePath(String storagePath) {
        return fileMapper.countByStoragePath(storagePath);
    }

    @Override
    public List<String> listAllStoragePaths() {
        return fileMapper.selectAllStoragePaths();
    }
}
