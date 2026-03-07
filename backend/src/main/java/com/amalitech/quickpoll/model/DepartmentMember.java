package com.amalitech.quickpoll.model;

import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "department_members", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"email", "department_id"})
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
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
    @Builder.Default
    private List<PollInvite> pollInvites = new ArrayList<>();

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DepartmentMember)) return false;
        DepartmentMember that = (DepartmentMember) o;
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
