package com.amalitech.quickpoll.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "poll_options")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class PollOption {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "poll_id")
    private Poll poll;

    @Column(name = "option_text", nullable = false)
    private String optionText;

    @Column(name = "vote_count")
    @Builder.Default
    private int voteCount = 0;
}
