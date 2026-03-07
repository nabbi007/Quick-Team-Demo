package com.amalitech.quickpoll.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "poll_options", indexes = {
    @Index(name = "idx_poll_option_poll", columnList = "poll_id")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class PollOption {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "poll_id")
    private Poll poll;

    @Column(name = "option_text", nullable = false)
    private String optionText;

    @Column(name = "vote_count")
    private int voteCount = 0;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PollOption)) return false;
        PollOption that = (PollOption) o;
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
