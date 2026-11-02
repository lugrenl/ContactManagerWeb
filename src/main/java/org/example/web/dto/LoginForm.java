package org.example.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class LoginForm {

    @NotBlank(message = "Введите имя пользователя.")
    @Size(min = 2, max = 20, message = "Имя пользователя должно содержать от 2 до 20 символов.")
    private String name;

    @NotBlank(message = "Введите пароль.")
    @Size(min = 8, max = 20, message = "Пароль должен содержать от 8 до 20 символов.")
    private String password;

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
}
