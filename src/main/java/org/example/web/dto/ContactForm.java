package org.example.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class ContactForm {

    @NotBlank(message = "Введите имя.")
    @Size(min = 2, max = 20, message = "Имя должно содержать от 2 до 20 символов.")
    private String name;

    @NotBlank(message = "Введите фамилию.")
    @Size(min = 2, max = 30, message = "Фамилия должна содержать от 2 до 30 символов.")
    private String surname;

    @Email(message = "Введите корректный адрес электронной почты.")
    private String email;

    @Pattern(regexp = "^$|^.{2,18}$", message = "Телефон должен содержать от 2 до 18 символов.")
    private String phoneNumber;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSurname() {
        return surname;
    }

    public void setSurname(String surname) {
        this.surname = surname;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }
}
