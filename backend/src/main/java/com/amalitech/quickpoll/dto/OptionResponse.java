package com.amalitech.quickpoll.dto;

import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class OptionResponse {
    private Long id;
    private String text;
    private int voteCount;
    private double percentage;
}
