package com.amalitech.quickpoll.service;

import com.amalitech.quickpoll.dto.DepartmentRequest;
import com.amalitech.quickpoll.dto.DepartmentResponse;
import com.amalitech.quickpoll.errorhandlers.ResourceAlreadyExistsException;
import com.amalitech.quickpoll.errorhandlers.ResourceNotFoundException;
import com.amalitech.quickpoll.mapper.DepartmentMapper;
import com.amalitech.quickpoll.model.Department;
import com.amalitech.quickpoll.model.DepartmentMember;
import com.amalitech.quickpoll.repository.DepartmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DepartmentService {
    private final DepartmentRepository departmentRepository;
    private final DepartmentMapper departmentMapper;

    @Transactional
    public DepartmentResponse createDepartment(DepartmentRequest request) {
        if (departmentRepository.existsByName(request.getName())) {
            throw new ResourceAlreadyExistsException("Department with name '" + request.getName() + "' already exists");
        }
        
        Department department = Department.builder()
                .name(request.getName())
                .build();

        List<DepartmentMember> members = request.getEmails().stream()
                .distinct()
                .map(email -> DepartmentMember.builder()
                        .email(email)
                        .department(department)
                        .build())
                .collect(Collectors.toList());
        department.setMembers(members);
        Department savedDepartment = departmentRepository.save(department);
        return departmentMapper.toResponse(savedDepartment);
    }

    public List<DepartmentResponse> getAllDepartments() {
        return departmentRepository.findAllWithMembers().stream()
                .map(departmentMapper::toResponse)
                .collect(Collectors.toList());
    }

    public DepartmentResponse getDepartmentById(Long id) {
        Department department = departmentRepository.findByIdWithMembers(id)
                .orElseThrow(() -> new ResourceNotFoundException("Department not found"));
        return departmentMapper.toResponse(department);
    }

    @Transactional
    public DepartmentResponse addEmailsToDepartment(Long departmentId, List<String> emails) {
        Department department = departmentRepository.findById(departmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Department not found"));
        
        List<String> existingEmails = department.getMembers().stream()
                .map(DepartmentMember::getEmail)
                .collect(Collectors.toList());
        
        List<DepartmentMember> newMembers = emails.stream()
                .distinct()
                .filter(email -> !existingEmails.contains(email))
                .map(email -> DepartmentMember.builder()
                        .email(email)
                        .department(department)
                        .build())
                .collect(Collectors.toList());
        
        department.getMembers().addAll(newMembers);
        Department savedDepartment = departmentRepository.save(department);
        return departmentMapper.toResponse(savedDepartment);
    }
}
