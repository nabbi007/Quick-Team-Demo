package com.amalitech.quickpoll.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.*;

@Builder
@Entity
@Table(name = "polls", indexes = {
    @Index(name = "idx_poll_creator", columnList = "creator_id"),
    @Index(name = "idx_poll_active", columnList = "active"),
    @Index(name = "idx_poll_expires_at", columnList = "expires_at"),
    @Index(name = "idx_poll_created_at", columnList = "created_at")
})
@Data @NoArgsConstructor @AllArgsConstructor
public class Poll {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;
    @Column(length = 1000, nullable = false)
    private String description;

    @Column(nullable = false)
    private String question;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "creator_id")
    private User creator;

    @OneToMany(mappedBy = "poll", cascade = CascadeType.ALL)
    @Builder.Default
    private List<PollOption> options = new ArrayList<>();

    @Column(name = "multi_select")
    private boolean multiSelect;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Builder.Default
    private boolean active = true;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() { createdAt = LocalDateTime.now(); }
}
