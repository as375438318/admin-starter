package com.adminstarter.system.infrastructure.persistence.repository;

import com.adminstarter.system.domain.model.FileChunkUpload;
import com.adminstarter.system.domain.repository.FileChunkUploadRepository;
import com.adminstarter.system.infrastructure.persistence.converter.FileChunkUploadConverter;
import com.adminstarter.system.infrastructure.persistence.entity.FileChunkUploadDO;
import com.adminstarter.system.infrastructure.persistence.mapper.FileChunkUploadMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class FileChunkUploadRepositoryImpl implements FileChunkUploadRepository {

    private final FileChunkUploadMapper mapper;
    private final FileChunkUploadConverter converter;

    @Override
    public void insert(FileChunkUpload session) {
        FileChunkUploadDO row = converter.toDO(session);
        mapper.insert(row);
        session.setId(row.getId());
    }

    @Override
    public FileChunkUpload findByUploadId(String uploadId) {
        return converter.toDomain(mapper.selectByUploadId(uploadId));
    }

    @Override
    public void updateChunks(String uploadId, String chunks) {
        mapper.updateChunks(uploadId, chunks);
    }

    @Override
    public void updateStatus(String uploadId, String status) {
        mapper.updateStatus(uploadId, status);
    }

    @Override
    public List<FileChunkUpload> findStaleUploading(LocalDateTime cutoff) {
        return mapper.selectStaleUploading(cutoff).stream().map(converter::toDomain).toList();
    }
}
