package com.amalitech.quickpoll.model;

import com.amalitech.quickpoll.model.enums.VoteStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "poll_invites")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
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
}
