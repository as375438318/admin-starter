package com.adminstarter.system.domain.repository;

import com.adminstarter.common.api.PageResult;
import com.adminstarter.system.domain.model.Customer;
import com.adminstarter.system.domain.query.CustomerQuery;

public interface CustomerRepository {

    Customer findById(Long id);

    void insert(Customer customer);

    void update(Customer customer);

    void deleteById(Long id);

    PageResult<Customer> page(CustomerQuery query);
}
