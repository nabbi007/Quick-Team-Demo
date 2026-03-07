package com.amalitech.quickpoll.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "department_members", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"email", "department_id"})
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DepartmentMember {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String email;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id")
    private Department department;

    @OneToMany(mappedBy = "departmentMember", cascade = CascadeType.ALL)
    private List<PollInvite> pollInvites = new ArrayList<>();
}
