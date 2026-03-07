package com.amalitech.quickpoll.repository;

import com.amalitech.quickpoll.model.PollInvite;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PollInviteRepository extends JpaRepository<PollInvite, Long> {
}
