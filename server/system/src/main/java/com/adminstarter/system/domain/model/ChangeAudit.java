package com.adminstarter.system.domain.model;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 配置面变更留痕（参数 / 字典 / 菜单 / 角色授权）。
 *
 * <p>与操作日志分工：操作日志记「请求」（谁调了什么接口、耗时、结果）；
 * 本模型记「对象轨迹」（这个配置从什么值变成了什么值），因此必须带 before/after。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ChangeAudit extends BaseModel {

    /** 对象类型：CONFIG / DICT_TYPE / DICT_ITEM / MENU / ROLE_GRANT */
    private String targetType;
    /** 对象业务键：参数键、字典编码、菜单 path、角色 code 等 */
    private String targetKey;
    /** 对象可读名（列表展示用） */
    private String targetName;
    /** 动作：CREATE / UPDATE / DELETE / GRANT */
    private String action;
    /** 变更前快照（JSON）；CREATE 为 null */
    private String beforeValue;
    /** 变更后快照（JSON）；DELETE 为 null */
    private String afterValue;
    /** 敏感对象：快照整体打码，禁止明文落库 */
    private Boolean isSensitive;
    private Long operatorId;
    private String operatorName;
    private String ip;
    private String traceId;
}