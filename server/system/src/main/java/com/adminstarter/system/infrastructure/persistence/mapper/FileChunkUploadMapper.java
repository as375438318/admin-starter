package com.adminstarter.system.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.adminstarter.system.infrastructure.persistence.entity.FileChunkUploadDO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

public interface FileChunkUploadMapper extends BaseMapper<FileChunkUploadDO> {

    @Select("SELECT * FROM sys_file_chunk_upload WHERE upload_id = #{uploadId}")
    FileChunkUploadDO selectByUploadId(@Param("uploadId") String uploadId);

    @Update("UPDATE sys_file_chunk_upload SET uploaded_chunks = #{chunks}, updated_at = UTC_TIMESTAMP() "
            + "WHERE upload_id = #{uploadId}")
    int updateChunks(@Param("uploadId") String uploadId, @Param("chunks") String chunks);

    @Update("UPDATE sys_file_chunk_upload SET status = #{status}, updated_at = UTC_TIMESTAMP() "
            + "WHERE upload_id = #{uploadId}")
    int updateStatus(@Param("uploadId") String uploadId, @Param("status") String status);

    @Select("SELECT * FROM sys_file_chunk_upload WHERE status = 'UPLOADING' AND updated_at < #{cutoff}")
    List<FileChunkUploadDO> selectStaleUploading(@Param("cutoff") LocalDateTime cutoff);
}
