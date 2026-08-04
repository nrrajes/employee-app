package com.nr.devops.employeeapp.repository;

import com.nr.devops.employeeapp.model.Employee;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmployeeRepository extends JpaRepository<Employee, Integer> {

}
