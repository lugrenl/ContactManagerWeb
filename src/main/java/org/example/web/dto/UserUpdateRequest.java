package org.example.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record UserUpdateRequest(
        String name,
        String password,
        String email,
        String role) {
}
