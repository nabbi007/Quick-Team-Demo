package com.amalitech.quickpoll.mapper;

import com.amalitech.quickpoll.dto.PollRequest;
import com.amalitech.quickpoll.dto.PollResponse;
import com.amalitech.quickpoll.model.Poll;
import com.amalitech.quickpoll.model.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", uses = {PollOptionMapper.class})
public interface PollMapper {
    
    @Mapping(target = "creatorName", source = "creator.fullName")
    @Mapping(target = "status", expression = "java(poll.isActive() ? \"ACTIVE\" : \"CLOSED\")")
    @Mapping(target = "multipleChoice", source = "multiSelect")
    @Mapping(target = "options", ignore = true)
    @Mapping(target = "totalVotes", ignore = true)
    PollResponse toResponse(Poll poll);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "title", source = "request.question")
    @Mapping(target = "question", source = "request.question")
    @Mapping(target = "description", source = "request.description")
    @Mapping(target = "multiSelect", source = "request.multipleChoice")
    @Mapping(target = "creator", source = "creator")
    @Mapping(target = "options", ignore = true)
    @Mapping(target = "active", constant = "true")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "expiresAt", ignore = true)
    Poll toEntity(PollRequest request, User creator);
}
