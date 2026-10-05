package com.example.leavemanagement;

import com.example.leavemanagement.controller.LeaveRequestsController;
import com.example.leavemanagement.dto.CreateLeaveRequestDto;
import com.example.leavemanagement.model.Employee;
import com.example.leavemanagement.model.LeaveType;
import com.example.leavemanagement.repository.EmployeeRepository;
import com.example.leavemanagement.repository.LeaveRequestRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

// Runs against a real, throwaway PostgreSQL started by Testcontainers.
// (Docker must be available on the machine running the tests.)
@SpringBootTest
@Testcontainers
class LeaveRequestsTests {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void datasourceProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private LeaveRequestsController controller;

    @Autowired
    private EmployeeRepository employees;

    @Autowired
    private LeaveRequestRepository leaveRequests;

    @Test
    void create_WithinQuota_Succeeds() {
        // Arrange
        Employee emp = new Employee();
        emp.setName("Test Emp");
        emp.setAnnualQuota(20);
        employees.save(emp);

        long before = leaveRequests.count();

        CreateLeaveRequestDto dto = new CreateLeaveRequestDto();
        dto.setEmployeeId(emp.getId());
        dto.setType(LeaveType.VACATION);
        dto.setStartDate(LocalDate.of(2026, 3, 1));
        dto.setEndDate(LocalDate.of(2026, 3, 3)); // 3 days, well within the quota

        // Act
        ResponseEntity<?> result = controller.create(dto);

        // Assert
        assertTrue(result.getStatusCode().is2xxSuccessful());
        assertEquals(before + 1, leaveRequests.count());
    }

    // TODO (candidate): add a test that proves the balance bug is fixed —
    // an employee who has already used most of the quota should NOT be able
    // to create a request that pushes them over the annual quota.
    @Test
    void create_WithinAvailableDays_Succeeds() {
        // Arrange
        Employee emp = new Employee();
        emp.setName("Test Emp");
        emp.setAnnualQuota(20);
        employees.save(emp);

        long before = leaveRequests.count();

        // Request "PREVIOUS" vacations
        CreateLeaveRequestDto dtoPrevious = new CreateLeaveRequestDto();
        dtoPrevious.setEmployeeId(emp.getId());
        dtoPrevious.setType(LeaveType.VACATION);
        dtoPrevious.setStartDate(LocalDate.of(2026, 3, 1));
        dtoPrevious.setEndDate(LocalDate.of(2026, 3, 18)); // 18 days, still within the quota (but remaining AVAILABLE days are 2)

        // Act
        ResponseEntity<?> resultPrevious = controller.create(dtoPrevious);

        // Request "PREVIOUS" vacations
        CreateLeaveRequestDto dtoNew = new CreateLeaveRequestDto();
        dtoNew.setEmployeeId(emp.getId());
        dtoNew.setType(LeaveType.VACATION);
        dtoNew.setStartDate(LocalDate.of(2026, 5, 1));
        dtoNew.setEndDate(LocalDate.of(2026, 5, 3)); // 3 days, within the quota, but beyond the Remaining-AVAILABLE days (=2)

        // Act
        ResponseEntity<?> resultNew = controller.create(dtoNew);


        // Assert
        assertTrue(resultNew.getStatusCode().is2xxSuccessful());
        assertEquals(before + 1, leaveRequests.count());
    }

}
