package com.adminstarter.system.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.adminstarter.system.infrastructure.persistence.entity.FileDO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

public interface FileMapper extends BaseMapper<FileDO> {

    String COLUMNS = "id, original_name, stored_name, storage_path, content_type, size, sha256, "
            + "uploader_id, dept_id, create_by, created_at, deleted, deleted_at, deleted_by";

    @Select("SELECT " + COLUMNS + " FROM sys_file WHERE sha256 = #{sha256} AND size = #{size} "
            + "AND uploader_id = #{uploaderId} ORDER BY id DESC LIMIT 1")
    FileDO selectByShaAndUploader(@Param("sha256") String sha256, @Param("size") long size,
                                  @Param("uploaderId") Long uploaderId);

    @Select("SELECT " + COLUMNS + " FROM sys_file WHERE sha256 = #{sha256} AND size = #{size} "
            + "ORDER BY id DESC LIMIT 1")
    FileDO selectBySha(@Param("sha256") String sha256, @Param("size") long size);

    @Select("SELECT " + COLUMNS + " FROM sys_file WHERE id = #{id} AND deleted = 1")
    FileDO selectDeletedById(@Param("id") Long id);

    @Select("<script>SELECT " + COLUMNS + " FROM sys_file WHERE deleted = 1 "
            + "<if test=\"keyword != null and keyword != ''\"> AND original_name LIKE CONCAT('%', #{keyword}, '%')</if>"
            + " ORDER BY deleted_at DESC, id DESC</script>")
    Page<FileDO> selectDeletedPage(Page<FileDO> page, @Param("keyword") String keyword);

    @Update("UPDATE sys_file SET deleted = 1, deleted_at = UTC_TIMESTAMP(), deleted_by = #{userId} "
            + "WHERE id = #{id} AND deleted = 0")
    int moveToRecycle(@Param("id") Long id, @Param("userId") Long userId);

    @Update("UPDATE sys_file SET deleted = 0, deleted_at = NULL, deleted_by = NULL "
            + "WHERE id = #{id} AND deleted = 1")
    int restore(@Param("id") Long id);

    @Delete("DELETE FROM sys_file WHERE id = #{id} AND deleted = 1")
    int purge(@Param("id") Long id);

    @Select("SELECT COUNT(*) FROM sys_file WHERE storage_path = #{storagePath}")
    long countByStoragePath(@Param("storagePath") String storagePath);

    @Select("SELECT DISTINCT storage_path FROM sys_file")
    List<String> selectAllStoragePaths();
}
