package org.example.web.dto;

public record ContactRequest(
        String name,
        String surname,
        String email,
        String phoneNumber) {
}
