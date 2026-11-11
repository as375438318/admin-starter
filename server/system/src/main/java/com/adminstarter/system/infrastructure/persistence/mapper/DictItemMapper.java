package com.adminstarter.system.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.adminstarter.system.infrastructure.persistence.entity.DictItemDO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;
import java.util.List;

public interface DictItemMapper extends BaseMapper<DictItemDO> {

    @Select("""
            <script>
            select i.id, i.dict_type_id, i.label, i.value, i.sort, i.is_enabled, i.remark,
                   i.created_at, i.updated_at, t.type as type_code
            from sys_dict_item i
            join sys_dict_type t on t.id = i.dict_type_id
            where i.deleted = 0 and i.is_enabled = 1 and t.deleted = 0 and t.is_enabled = 1
              and t.type in
            <foreach collection="types" item="type" open="(" separator="," close=")">#{type}</foreach>
            order by t.type asc, i.sort asc, i.id asc
            </script>
            """)
    List<DictItemDO> selectByTypeCodes(@Param("types") Collection<String> types);
}
