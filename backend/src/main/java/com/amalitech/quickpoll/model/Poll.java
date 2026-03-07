package com.amalitech.quickpoll.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.*;

@Entity
@Table(name = "polls", indexes = {
    @Index(name = "idx_poll_creator", columnList = "creator_id"),
    @Index(name = "idx_poll_active", columnList = "active"),
    @Index(name = "idx_poll_expires_at", columnList = "expires_at"),
    @Index(name = "idx_poll_created_at", columnList = "created_at")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder @ToString
public class Poll {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String question;
    
    @Column(length = 1000)
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "creator_id")
    @ToString.Exclude
    private User creator;

    @OneToMany(mappedBy = "poll", cascade = CascadeType.ALL)
    @Builder.Default
    @ToString.Exclude
    private List<PollOption> options = new ArrayList<>();

    @OneToMany(mappedBy = "poll", cascade = CascadeType.ALL)
    @Builder.Default
    @ToString.Exclude
    private List<PollInvite> invites = new ArrayList<>();

    @Column(name = "multi_select")
    private boolean multiSelect;

    @Column(nullable = false, name = "anonymous")
    private boolean anonymous;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Builder.Default
    private boolean active = true;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() { createdAt = LocalDateTime.now(); }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Poll)) return false;
        Poll poll = (Poll) o;
        return id != null && id.equals(poll.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
