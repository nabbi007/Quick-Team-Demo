package com.amalitech.quickpoll.mapper;

import com.amalitech.quickpoll.dto.DepartmentResponse;
import com.amalitech.quickpoll.model.Department;
import com.amalitech.quickpoll.model.DepartmentMember;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;
import java.util.stream.Collectors;

@Mapper(componentModel = "spring")
public interface DepartmentMapper {
    
    @Mapping(target = "emails", expression = "java(mapMembersToEmails(department.getMembers()))")
    DepartmentResponse toResponse(Department department);
    
    default List<String> mapMembersToEmails(List<DepartmentMember> members) {
        return members.stream()
                .map(DepartmentMember::getEmail)
                .collect(Collectors.toList());
    }
}
