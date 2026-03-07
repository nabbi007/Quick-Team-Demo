package com.amalitech.quickpoll.model;

import com.amalitech.quickpoll.model.enums.VoteStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "poll_invites")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PollInvite {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "poll_id")
    private Poll poll;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_member_id")
    private DepartmentMember departmentMember;

    @Column(name = "invited_at")
    private LocalDateTime invitedAt;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private VoteStatus voteStatus = VoteStatus.PENDING;

    @PrePersist
    protected void onCreate() {
        invitedAt = LocalDateTime.now();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PollInvite)) return false;
        PollInvite that = (PollInvite) o;
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
