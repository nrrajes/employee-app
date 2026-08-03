package com.nr.devops.employeeapp.service;

import com.nr.devops.employeeapp.model.Employee;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class EmployeeService {

    public List<Employee> getEmployees() {

        return List.of(
                new Employee(1, "Rajesh", "DevOps"),
                new Employee(2, "John", "Cloud"),
                new Employee(3, "Alice", "Linux")
        );
    }
}
