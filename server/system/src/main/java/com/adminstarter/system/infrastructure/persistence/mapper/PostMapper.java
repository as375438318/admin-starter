package com.adminstarter.system.infrastructure.persistence.mapper;

import com.adminstarter.system.infrastructure.persistence.entity.PostDO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface PostMapper extends BaseMapper<PostDO> {

    /**
     * 多行 VALUES 批量插入。不声明 useGeneratedKeys：导入路径不使用自增主键；
     * 若将来需要回填，可加 useGeneratedKeys/keyProperty="rows.id"（MySQL 多行插入会返回全部自增）。
     */
    @Insert("""
            <script>
            INSERT INTO sys_post (dept_id, code, name, sort, is_enabled, created_at, updated_at, deleted)
            VALUES
            <foreach collection="rows" item="row" separator=",">
                (#{row.deptId}, #{row.code}, #{row.name}, #{row.sort}, #{row.isEnabled}, #{row.createdAt}, #{row.updatedAt}, 0)
            </foreach>
            </script>
            """)
    int insertBatch(@Param("rows") List<PostDO> rows);
}
