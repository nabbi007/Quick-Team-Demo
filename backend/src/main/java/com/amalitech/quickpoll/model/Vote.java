package com.amalitech.quickpoll.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "votes", 
    uniqueConstraints = {
        @UniqueConstraint(columnNames = {"poll_id", "user_id"})
    },
    indexes = {
        @Index(name = "idx_vote_poll", columnList = "poll_id"),
        @Index(name = "idx_vote_user", columnList = "user_id"),
        @Index(name = "idx_vote_option", columnList = "option_id"),
        @Index(name = "idx_vote_created_at", columnList = "created_at")
    }
)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class Vote {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "poll_id")
    private Poll poll;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "option_id")
    private PollOption option;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() { createdAt = LocalDateTime.now(); }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Vote)) return false;
        Vote vote = (Vote) o;
        return id != null && id.equals(vote.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
