package org.example.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class RegisterForm {

    @NotBlank(message = "Введите имя пользователя.")
    @Size(min = 2, max = 20, message = "Имя пользователя должно содержать от 2 до 20 символов.")
    private String name;

    @NotBlank(message = "Введите пароль.")
    @Size(min = 8, max = 20, message = "Пароль должен содержать от 8 до 20 символов.")
    private String password;

    @NotBlank(message = "Введите адрес электронной почты.")
    @Email(message = "Введите корректный адрес электронной почты.")
    private String email;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }
}
