package com.amalitech.quickpoll.repository;

import com.amalitech.quickpoll.model.Vote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
@Repository
public interface VoteRepository extends JpaRepository<Vote, Long> {
    List<Vote> findByOptionPollId(Long pollId);
    Optional<Vote> findByUserIdAndOptionPollId(Long userId, Long pollId);
    boolean existsByUserIdAndOptionPollId(Long userId, Long pollId);
    int countByOptionId(Long optionId);
    
    @Query("SELECT v.option.id, COUNT(v) FROM Vote v WHERE v.option.id IN :optionIds GROUP BY v.option.id")
    List<Object[]> countVotesByOptionIds(@Param("optionIds") List<Long> optionIds);
}
