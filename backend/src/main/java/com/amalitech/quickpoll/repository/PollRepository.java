package com.amalitech.quickpoll.repository;

import com.amalitech.quickpoll.model.Poll;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
@Repository
public interface PollRepository extends JpaRepository<Poll, Long> {
    Page<Poll> findAllByOrderByCreatedAtDesc(Pageable pageable);
    
    @Query("SELECT DISTINCT p FROM Poll p LEFT JOIN FETCH p.options ORDER BY p.createdAt DESC")
    List<Poll> findAllWithOptions();
    
    @Query("SELECT p FROM Poll p LEFT JOIN FETCH p.options WHERE p.id = :id")
    Optional<Poll> findByIdWithOptions(@Param("id") Long id);
    
    List<Poll> findByCreatorIdOrderByCreatedAtDesc(Long creatorId);
    
    @Modifying
    @Query("UPDATE Poll p SET p.active = false WHERE p.active = true AND p.expiresAt <= :now")
    int closeExpiredPolls(@Param("now") LocalDateTime now);
}
