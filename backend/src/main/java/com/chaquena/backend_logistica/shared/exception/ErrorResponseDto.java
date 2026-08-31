package com.chaquena.backend_logistica.shared.exception;

import lombok.*;

import java.time.ZonedDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ErrorResponseDto {
    private ZonedDateTime timestamp;
    private int status;
    private String error;
    private String message;
    private String path;
    private List<String> details;
}