package com.adminstarter.web.controller;

import com.adminstarter.common.api.ApiResponse;
import com.adminstarter.common.api.PageQuery;
import com.adminstarter.common.api.PageResponse;
import com.adminstarter.system.application.DictAppService;
import com.adminstarter.system.application.dto.DictDtos;
import com.adminstarter.web.log.OperLogRecord;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/dicts")
@RequiredArgsConstructor
public class DictController {

    private final DictAppService dictAppService;

    @GetMapping("/types")
    @PreAuthorize("hasAuthority('system:dict:list')")
    public ApiResponse<PageResponse<DictDtos.TypeResponse>> pageTypes(PageQuery pageQuery,
                                                                      @RequestParam(required = false) String keyword,
                                                                      @RequestParam(required = false) Boolean isEnabled) {
        return ApiResponse.ok(dictAppService.pageTypes(pageQuery, keyword, isEnabled));
    }

    @GetMapping("/types/{id}/items")
    @PreAuthorize("hasAuthority('system:dict:list')")
    public ApiResponse<List<DictDtos.ItemResponse>> items(@PathVariable Long id) {
        return ApiResponse.ok(dictAppService.items(id));
    }

    /**
     * 按类型批量查询，任意登录用户可用（业务页面下拉数据）。
     */
    @GetMapping("/bundles")
    public ApiResponse<List<DictDtos.TypeBundle>> bundles(@RequestParam List<String> types) {
        return ApiResponse.ok(dictAppService.bundles(types));
    }

    @PostMapping("/types")
    @PreAuthorize("hasAuthority('system:dict:create')")
    @OperLogRecord(module = "字典管理", action = "新增字典类型")
    public ApiResponse<DictDtos.TypeResponse> createType(@Valid @RequestBody DictDtos.TypeSaveRequest request) {
        return ApiResponse.ok(dictAppService.createType(request));
    }

    @PutMapping("/types/{id}")
    @PreAuthorize("hasAuthority('system:dict:update')")
    @OperLogRecord(module = "字典管理", action = "修改字典类型")
    public ApiResponse<DictDtos.TypeResponse> updateType(@PathVariable Long id,
                                                         @Valid @RequestBody DictDtos.TypeSaveRequest request) {
        return ApiResponse.ok(dictAppService.updateType(id, request));
    }

    @DeleteMapping("/types/{id}")
    @PreAuthorize("hasAuthority('system:dict:delete')")
    @OperLogRecord(module = "字典管理", action = "删除字典类型")
    public ApiResponse<Void> deleteType(@PathVariable Long id) {
        dictAppService.deleteType(id);
        return ApiResponse.ok();
    }

    @PostMapping("/items")
    @PreAuthorize("hasAuthority('system:dict:create')")
    @OperLogRecord(module = "字典管理", action = "新增字典项")
    public ApiResponse<DictDtos.ItemResponse> createItem(@Valid @RequestBody DictDtos.ItemSaveRequest request) {
        return ApiResponse.ok(dictAppService.createItem(request));
    }

    @PutMapping("/items/{id}")
    @PreAuthorize("hasAuthority('system:dict:update')")
    @OperLogRecord(module = "字典管理", action = "修改字典项")
    public ApiResponse<DictDtos.ItemResponse> updateItem(@PathVariable Long id,
                                                         @Valid @RequestBody DictDtos.ItemSaveRequest request) {
        return ApiResponse.ok(dictAppService.updateItem(id, request));
    }

    @DeleteMapping("/items/{id}")
    @PreAuthorize("hasAuthority('system:dict:delete')")
    @OperLogRecord(module = "字典管理", action = "删除字典项")
    public ApiResponse<Void> deleteItem(@PathVariable Long id) {
        dictAppService.deleteItem(id);
        return ApiResponse.ok();
    }
}
