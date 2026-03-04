package com.amalitech.quickpoll.repository;

import com.amalitech.quickpoll.model.Poll;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.List;

public interface PollRepository extends JpaRepository<Poll, Long> {
    Page<Poll> findAllByOrderByCreatedAtDesc(Pageable pageable);
    List<Poll> findByCreatorIdOrderByCreatedAtDesc(Long creatorId);
    // TODO: Add search and filter methods
}
