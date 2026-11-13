package com.adminstarter.system.infrastructure.persistence.repository;

import com.adminstarter.common.api.PageResult;
import com.adminstarter.system.domain.model.Customer;
import com.adminstarter.system.domain.query.CustomerQuery;
import com.adminstarter.system.domain.repository.CustomerRepository;
import com.adminstarter.system.infrastructure.persistence.SortSupport;
import com.adminstarter.system.infrastructure.persistence.converter.CustomerConverter;
import com.adminstarter.system.infrastructure.persistence.entity.CustomerDO;
import com.adminstarter.system.infrastructure.persistence.mapper.CustomerMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Map;

@Repository
@RequiredArgsConstructor
public class CustomerRepositoryImpl implements CustomerRepository {

    /**
     * 可排序字段白名单（前端 `sortKey` 只能取这里的值）。
     * 用 `SortSupport.resolveColumn` 解析：白名单外与 null 都会回落默认列，
     * **禁止直接写 `SORTS.get(sort)`**——不可变 Map 的 get(null) 会抛 NPE（断言 #103）。
     */
    private static final Map<String, String> SORTS = Map.of(
            "createdAt", "created_at",
            "name", "name",
            "level", "level",
            "id", "id"
    );

    private final CustomerMapper customerMapper;
    private final CustomerConverter converter;

    @Override
    public Customer findById(Long id) {
        return converter.toDomain(customerMapper.selectById(id));
    }

    @Override
    public void insert(Customer customer) {
        CustomerDO row = converter.toDO(customer);
        customerMapper.insert(row);
        customer.setId(row.getId());
        customer.setCreatedAt(row.getCreatedAt());
        customer.setUpdatedAt(row.getUpdatedAt());
    }

    @Override
    public void update(Customer customer) {
        customerMapper.updateById(converter.toDO(customer));
    }

    @Override
    public void deleteById(Long id) {
        customerMapper.deleteById(id);
    }

    @Override
    public PageResult<Customer> page(CustomerQuery query) {
        Page<CustomerDO> page = customerMapper.selectPage(new Page<>(query.getPage(), query.getPageSize()), wrapper(query));
        return PageResult.of(page.getRecords().stream().map(converter::toDomain).toList(), page.getTotal());
    }

    private QueryWrapper<CustomerDO> wrapper(CustomerQuery query) {
        QueryWrapper<CustomerDO> wrapper = new QueryWrapper<>();
        if (query.getKeyword() != null && !query.getKeyword().isBlank()) {
            wrapper.and(w -> w.like("name", query.getKeyword()).or().like("contact", query.getKeyword()));
        }
        String column = SortSupport.resolveColumn(SORTS, query.getSort(), "created_at");
        SortSupport.applyOrder(wrapper, column, SortSupport.isAsc(query.getOrder(), SortSupport.NEWEST_FIRST));
        return wrapper;
    }
}
