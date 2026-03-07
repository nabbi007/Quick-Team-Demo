package com.amalitech.quickpoll.service;

import com.amalitech.quickpoll.dto.*;
import com.amalitech.quickpoll.errorhandlers.ResourceNotFoundException;
import com.amalitech.quickpoll.mapper.PollMapper;
import com.amalitech.quickpoll.mapper.PollOptionMapper;
import com.amalitech.quickpoll.model.*;
import com.amalitech.quickpoll.model.enums.Role;
import com.amalitech.quickpoll.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class PollService {
    private final PollRepository pollRepository;
    private final PollOptionRepository optionRepository;
    private final VoteRepository voteRepository;
    private final PollMapper pollMapper;
    private final PollOptionMapper pollOptionMapper;
    private final DepartmentRepository departmentRepository;
    private final PollInviteRepository pollInviteRepository;

    public Page<PollResponse> getAllPolls(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return pollRepository.findAllByOrderByCreatedAtDesc(pageable)
                .map(this::toResponse);
    }

    public PollResponse getPollById(Long id) {
        Poll poll = pollRepository.findByIdWithOptions(id)
                .orElseThrow(() -> new ResourceNotFoundException("Poll not found"));
        return toResponse(poll);
    }

    @Transactional
    public PollResponse createPoll(PollRequest request, User creator) {
        Poll poll = pollMapper.toEntity(request, creator);
        log.info("Creating poll: {}", poll);
        Poll savedPoll = pollRepository.save(poll);

        List<PollOption> options = request.getOptions().stream()
                .map(optionText -> {
                    PollOption option = new PollOption();
                    option.setOptionText(optionText);
                    option.setPoll(savedPoll);
                    return option;
                })
                .toList();
        optionRepository.saveAll(options);
        
        List<Department> departments = departmentRepository.findAllByIdInWithMembers(request.getDepartmentIds());
        log.info("Found {} departments for IDs: {}", departments.size(), request.getDepartmentIds());
        
        List<PollInvite> invites = departments.stream()
                .flatMap(department -> {
                    log.info("Department '{}' has {} members", department.getName(), department.getMembers().size());
                    return department.getMembers().stream();
                })
                .map(member -> PollInvite.builder()
                        .poll(savedPoll)
                        .departmentMember(member)
                        .build())
                .toList();
        log.info("Created {} poll invites", invites.size());
        pollInviteRepository.saveAll(invites);
        
        return toResponse(savedPoll);
    }

    // TODO: Implement vote method
    // public void vote(Long pollId, VoteRequest request, User voter) { ... }

    public  PollResponse closePoll(Long pollId, User user) {
        Poll poll = pollRepository.findById(pollId)
                .orElseThrow(() -> new ResourceNotFoundException("Poll not found"));

        if (!poll.getCreator().getId().equals(user.getId()) && user.getRole() != Role.ADMIN) {
            throw new AccessDeniedException("Only the creator or admin can close this poll");
        }

        if (!poll.isActive()) {
            throw new IllegalStateException("Poll is already closed");
        }

        poll.setActive(false);
        Poll closedPoll = pollRepository.save(poll);
        return toResponse(closedPoll);
    }



    public Boolean deletePoll(Long pollId, User user) {
        Poll poll = pollRepository.findById(pollId)
                .orElseThrow(() -> new ResourceNotFoundException("Poll not found"));

        if (!poll.getCreator().getId().equals(user.getId()) && user.getRole() != Role.ADMIN) {
            throw new AccessDeniedException("Only the creator or admin can delete this poll");
        }

        pollRepository.delete(poll);
        return true;
    }

    public PollResponse getPollResults(Long pollId) {
        Poll poll = pollRepository.findByIdWithOptions(pollId)
                .orElseThrow(() -> new ResourceNotFoundException("Poll not found"));
        return toResponse(poll);
    }

    private PollResponse toResponse(Poll poll) {
        List<PollOption> options = poll.getOptions();
        List<Long> optionIds = options.stream().map(PollOption::getId).toList();
        
        java.util.Map<Long, Integer> voteCounts = voteRepository.countVotesByOptionIds(optionIds)
                .stream()
                .collect(java.util.stream.Collectors.toMap(
                    result -> (Long) result[0],
                    result -> ((Number) result[1]).intValue()
                ));
        
        int totalVotes = voteCounts.values().stream().mapToInt(Integer::intValue).sum();

        List<OptionResponse> optionResponses = options.stream().map(o -> {
            int count = voteCounts.getOrDefault(o.getId(), 0);
            OptionResponse response = pollOptionMapper.toResponse(o);
            response.setVoteCount(count);
            response.setPercentage(totalVotes > 0 ? (count * 100.0 / totalVotes) : 0);
            return response;
        }).toList();

        PollResponse response = pollMapper.toResponse(poll);
        response.setTotalVotes(totalVotes);
        response.setOptions(optionResponses);
        return response;
    }
}
