package com.adminstarter.system.application;

import com.adminstarter.common.api.PageQuery;
import com.adminstarter.common.api.PageResponse;
import com.adminstarter.common.api.PageResult;
import com.adminstarter.common.error.BizException;
import com.adminstarter.common.error.ErrorCode;
import com.adminstarter.common.util.TimeSupport;
import com.adminstarter.system.application.dto.CustomerDtos;
import com.adminstarter.system.domain.model.Customer;
import com.adminstarter.system.domain.query.CustomerQuery;
import com.adminstarter.system.domain.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CustomerAppService {

    private final CustomerRepository customerRepository;

    public PageResponse<CustomerDtos.Response> page(PageQuery pageQuery, String keyword) {
        int page = pageQuery.resolvePage();
        int pageSize = pageQuery.resolvePageSize();
        CustomerQuery query = new CustomerQuery();
        query.setKeyword(keyword);
        query.setPage(page);
        query.setPageSize(pageSize);
        query.setSort(pageQuery.getSort());
        query.setOrder(pageQuery.getOrder());
        PageResult<Customer> result = customerRepository.page(query);
        List<CustomerDtos.Response> list = result.getList().stream().map(this::toResponse).toList();
        return PageResponse.of(page, pageSize, list, result.getTotal());
    }

    public CustomerDtos.Response get(Long id) {
        return toResponse(require(id));
    }

    @Transactional
    public CustomerDtos.Response create(CustomerDtos.SaveRequest request) {
        Customer customer = new Customer();
        apply(customer, request);
        customerRepository.insert(customer);
        return toResponse(customer);
    }

    @Transactional
    public CustomerDtos.Response update(Long id, CustomerDtos.SaveRequest request) {
        Customer customer = require(id);
        apply(customer, request);
        customerRepository.update(customer);
        return toResponse(customer);
    }

    @Transactional
    public void delete(Long id) {
        require(id);
        customerRepository.deleteById(id);
    }

    private Customer require(Long id) {
        Customer customer = customerRepository.findById(id);
        if (customer == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "客户档案不存在");
        }
        return customer;
    }

    private void apply(Customer target, CustomerDtos.SaveRequest request) {
        target.setName(request.getName() == null ? null : request.getName().trim());
        target.setLevel(request.getLevel() == null ? null : request.getLevel().trim());
        target.setContact(request.getContact() == null ? null : request.getContact().trim());
        target.setPhone(request.getPhone() == null ? null : request.getPhone().trim());
        target.setIsEnabled(request.getIsEnabled());
        target.setRemark(request.getRemark() == null ? null : request.getRemark().trim());
    }

    private CustomerDtos.Response toResponse(Customer source) {
        CustomerDtos.Response response = new CustomerDtos.Response();
        response.setId(source.getId());
        response.setName(source.getName());
        response.setLevel(source.getLevel());
        response.setContact(source.getContact());
        response.setPhone(source.getPhone());
        response.setIsEnabled(source.getIsEnabled());
        response.setRemark(source.getRemark());
        response.setCreatedAt(TimeSupport.toInstant(source.getCreatedAt()));
        response.setUpdatedAt(TimeSupport.toInstant(source.getUpdatedAt()));
        return response;
    }
}
