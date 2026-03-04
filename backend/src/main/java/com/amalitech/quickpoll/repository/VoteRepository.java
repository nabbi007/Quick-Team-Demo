package com.amalitech.quickpoll.repository;

import com.amalitech.quickpoll.model.Vote;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface VoteRepository extends JpaRepository<Vote, Long> {
    List<Vote> findByPollOptionPollId(Long pollId);
    Optional<Vote> findByUserIdAndPollOptionPollId(Long userId, Long pollId);
    boolean existsByUserIdAndPollOptionPollId(Long userId, Long pollId);
    int countByPollOptionId(Long optionId);
}
