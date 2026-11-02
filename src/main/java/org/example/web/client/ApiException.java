package org.example.web.client;

import java.util.List;

public class ApiException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final int status;
    private final List<String> details;

    public ApiException(int status, String message) {
        this(status, message, List.of());
    }

    public ApiException(int status, String message, List<String> details) {
        super(normalize(message));
        this.status = status;
        this.details = details == null ? List.of() : List.copyOf(details);
    }

    public int getStatus() {
        return status;
    }

    public List<String> getDetails() {
        return details;
    }

    private static String normalize(String message) {
        return message == null || message.isBlank() ? "Ошибка при обращении к API" : message;
    }
}
