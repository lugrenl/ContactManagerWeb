package org.example.web.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record UserDto(
        long id,
        String name,
        String email,
        String role) {

    public long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }

    public String getRole() {
        return role;
    }

    public String getRoleLabel() {
        if ("ROLE_ADMIN".equals(role)) {
            return "Администратор";
        }
        if ("ROLE_USER".equals(role)) {
            return "Пользователь";
        }
        return role == null || role.isBlank() ? "Не указана" : role;
    }
}
