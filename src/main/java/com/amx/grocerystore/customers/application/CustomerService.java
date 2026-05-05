package com.amx.grocerystore.customers.application;

import com.amx.grocerystore.core.security.SecurityUtils;
import com.amx.grocerystore.core.web.dto.PageResponse;
import com.amx.grocerystore.core.web.exception.NotFoundException;
import com.amx.grocerystore.customers.domain.Customer;
import com.amx.grocerystore.customers.infrastructure.CustomerRepository;
import com.amx.grocerystore.customers.web.dto.CreateCustomerRequest;
import com.amx.grocerystore.customers.web.dto.CustomerResponse;
import com.amx.grocerystore.customers.web.dto.UpdateCustomerRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CustomerService {

    private final CustomerRepository customerRepository;

    @Transactional
    public CustomerResponse create(CreateCustomerRequest request) {
        UUID storeId = SecurityUtils.currentStoreId();
        Customer customer = Customer.builder()
                .storeId(storeId)
                .fullName(request.fullName())
                .phone(request.phone())
                .outstandingBalance(BigDecimal.ZERO)
                .creditLimit(request.creditLimit())
                .active(true)
                .build();
        return CustomerResponse.from(customerRepository.save(customer));
    }

    @Transactional(readOnly = true)
    public CustomerResponse findById(UUID id) {
        UUID storeId = SecurityUtils.currentStoreId();
        Customer customer = customerRepository.findByIdAndStoreId(id, storeId)
                .orElseThrow(() -> NotFoundException.customer(id));
        return CustomerResponse.from(customer);
    }

    @Transactional(readOnly = true)
    public PageResponse<CustomerResponse> list(Boolean active, String search, Pageable pageable) {
        UUID storeId = SecurityUtils.currentStoreId();
        String searchTerm = (search == null) ? "" : search;
        Page<Customer> page = customerRepository.findCustomers(storeId, active, searchTerm, pageable);
        return PageResponse.from(page.map(CustomerResponse::from));
    }

    @Transactional
    public CustomerResponse update(UUID id, UpdateCustomerRequest request) {
        UUID storeId = SecurityUtils.currentStoreId();
        Customer customer = customerRepository.findByIdAndStoreId(id, storeId)
                .orElseThrow(() -> NotFoundException.customer(id));
        customer.setFullName(request.fullName());
        customer.setPhone(request.phone());
        customer.setCreditLimit(request.creditLimit());
        return CustomerResponse.from(customerRepository.save(customer));
    }

    @Transactional
    public void softDelete(UUID id) {
        UUID storeId = SecurityUtils.currentStoreId();
        Customer customer = customerRepository.findByIdAndStoreId(id, storeId)
                .orElseThrow(() -> NotFoundException.customer(id));
        if (!customer.isActive()) {
            return;
        }
        customer.setActive(false);
        customerRepository.save(customer);
    }
}
