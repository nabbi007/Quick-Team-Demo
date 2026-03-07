package com.amalitech.quickpoll.repository;

import com.amalitech.quickpoll.model.Department;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DepartmentRepository extends JpaRepository<Department, Long> {
    boolean existsByName(String name);
    
    @Query("SELECT DISTINCT d FROM Department d LEFT JOIN FETCH d.members")
    List<Department> findAllWithMembers();
    
    @Query("SELECT d FROM Department d LEFT JOIN FETCH d.members WHERE d.id = :id")
    java.util.Optional<Department> findByIdWithMembers(Long id);
    
    @Query("SELECT DISTINCT d FROM Department d LEFT JOIN FETCH d.members WHERE d.id IN :ids")
    List<Department> findAllByIdInWithMembers(List<Long> ids);
}
