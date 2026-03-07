package com.amalitech.quickpoll.controller;

import com.amalitech.quickpoll.dto.AddEmailsRequest;
import com.amalitech.quickpoll.dto.DepartmentRequest;
import com.amalitech.quickpoll.dto.DepartmentResponse;
import com.amalitech.quickpoll.service.DepartmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/departments")
@RequiredArgsConstructor
@Tag(name = "Departments", description = "Department management endpoints")
@SecurityRequirement(name = "Bearer Authentication")
public class DepartmentController {
    private final DepartmentService departmentService;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')") 
    @Operation(summary = "Create department", description = "Create a new department with members")
    public ResponseEntity<DepartmentResponse> createDepartment(@Valid @RequestBody DepartmentRequest request) {
        return ResponseEntity.ok(departmentService.createDepartment(request));
    }

    @GetMapping
    @Operation(summary = "Get all departments", description = "Retrieve all departments with their members")
    public ResponseEntity<List<DepartmentResponse>> getAllDepartments() {
        return ResponseEntity.ok(departmentService.getAllDepartments());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get department by ID", description = "Retrieve a specific department with its members")
    public ResponseEntity<DepartmentResponse> getDepartmentById(@PathVariable Long id) {
        return ResponseEntity.ok(departmentService.getDepartmentById(id));
    }

    @PostMapping("/{id}/emails")
    @Operation(summary = "Add emails to department", description = "Add new member emails to an existing department")
    public ResponseEntity<DepartmentResponse> addEmailsToDepartment(
            @PathVariable Long id,
            @Valid @RequestBody AddEmailsRequest request) {
        return ResponseEntity.ok(departmentService.addEmailsToDepartment(id, request.getEmails()));
    }
}
