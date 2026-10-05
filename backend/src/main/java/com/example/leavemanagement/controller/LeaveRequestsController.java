package com.example.leavemanagement.controller;

import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.leavemanagement.dto.CreateLeaveRequestDto;
import com.example.leavemanagement.model.Employee;
import com.example.leavemanagement.model.LeaveRequest;
import com.example.leavemanagement.model.LeaveStatus;
import com.example.leavemanagement.model.LeaveType;
import com.example.leavemanagement.repository.EmployeeRepository;
import com.example.leavemanagement.repository.LeaveRequestRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;

// NOTE: This controller was written quickly for a POC.
// It does data access, business logic and validation all in one place.
@RestController
@RequestMapping("/api/leave-requests")
public class LeaveRequestsController {

    private final EmployeeRepository employeeRepository;
    private final LeaveRequestRepository leaveRequestRepository;

    @PersistenceContext
    private EntityManager entityManager;

    public LeaveRequestsController(EmployeeRepository employeeRepository,
                                   LeaveRequestRepository leaveRequestRepository) {
        this.employeeRepository = employeeRepository;
        this.leaveRequestRepository = leaveRequestRepository;
    }

    // GET /api/leave-requests
    @GetMapping
    public ResponseEntity<List<LeaveRequest>> getAll() {
        List<LeaveRequest> all = leaveRequestRepository.findAll().stream()
                .sorted((a, b) -> b.getStartDate().compareTo(a.getStartDate()))
                .toList();
        return ResponseEntity.ok(all);
    }

    // GET /api/leave-requests/search?name=Dana
    // Lets the UI quickly find requests by employee name.
    @GetMapping("/search")
    public ResponseEntity<List<LeaveRequest>> search(@RequestParam String name) {
        // Build a quick query to filter by the employee name.
        String sql = "SELECT * FROM leave_requests WHERE employee_id IN " +
                "(SELECT id FROM employees WHERE name LIKE '%" + name + "%')";

        @SuppressWarnings("unchecked")
        List<LeaveRequest> results = entityManager
                .createNativeQuery(sql, LeaveRequest.class)
                .getResultList();

        return ResponseEntity.ok(results);
    }

    @Transactional
    public LeaveRequest updateStatus( Long leaveRequestId, LeaveStatus newStatus ) {
        LeaveRequest theLeaveRequest = leaveRequestRepository.getReferenceById(leaveRequestId);

        if( theLeaveRequest == null ) {
            throw new RuntimeException( String.format("Leave-Request %d not found.", leaveRequestId)  );
        }

        LeaveRequest result = theLeaveRequest;

        if( theLeaveRequest.getStatus() !=  LeaveStatus.APPROVED) {
            theLeaveRequest.setStatus(LeaveStatus.APPROVED);
            result = leaveRequestRepository.save(theLeaveRequest);
        }

        return result;
    }

    // POST /api/leave-requests/{id}/approve
    @PostMapping("/{id}/approve")
    public ResponseEntity<?> approve(@PathVariable Long id) {
        updateStatus(id, LeaveStatus.APPROVED);
        return ResponseEntity.ok( "So far, so good" );
    }

    // POST /api/leave-requests
    @PostMapping
    public ResponseEntity<?> create(@RequestBody CreateLeaveRequestDto dto) {
        Employee employee = employeeRepository.findById(dto.getEmployeeId()).orElse(null);
        if (employee == null) {
            return ResponseEntity.status(404).body("Employee not found");
        }

        int days = (int) ChronoUnit.DAYS.between(dto.getStartDate(), dto.getEndDate()) + 1;

        // How many vacation days has the employee already used this year?
        int used = leaveRequestRepository
                .findByEmployeeIdAndTypeAndStatus(dto.getEmployeeId(), LeaveType.VACATION, LeaveStatus.APPROVED)
                .stream()
                .mapToInt(LeaveRequest::getDays)
                .sum();

        // Make sure the request does not exceed the quota.
        int daysAvailable = employee.getAnnualQuota() - used;
        boolean isExceedingQuota = days > daysAvailable;

        if (dto.getType() == LeaveType.VACATION && isExceedingQuota ) {
            return ResponseEntity.badRequest().body("Not enough vacation balance");
        }

        LeaveRequest request = new LeaveRequest();
        request.setEmployeeId(dto.getEmployeeId());
        request.setType(dto.getType());
        request.setStartDate(dto.getStartDate());
        request.setEndDate(dto.getEndDate());
        request.setDays(days);
        request.setStatus(LeaveStatus.PENDING);

        leaveRequestRepository.save(request);

        return ResponseEntity.ok(request);
    }
}
