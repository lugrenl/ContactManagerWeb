package org.example.web.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ContactDto(
        long id,
        String name,
        String surname,
        String email,
        String phoneNumber) {

    public long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getSurname() {
        return surname;
    }

    public String getEmail() {
        return email;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public String getDisplayName() {
        String surnamePart = surname == null ? "" : surname;
        String namePart = name == null ? "" : name;
        return (surnamePart + " " + namePart).trim();
    }
}
