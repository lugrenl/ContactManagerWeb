package org.example.web.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

public final class ApiErrors {

    private static final int MAX_DETAILS = 10;
    private static final int MAX_DETAIL_LENGTH = 300;

    private ApiErrors() {
    }

    public static ApiException from(int status, String responseBody, ObjectMapper objectMapper) {
        return new ApiException(status, defaultMessage(status), extractDetails(responseBody, objectMapper));
    }

    public static ApiException from(int status, String responseBody, ObjectMapper objectMapper, String message) {
        return new ApiException(status, message, extractDetails(responseBody, objectMapper));
    }

    public static ApiException unavailable() {
        return new ApiException(0,
                "Сервер API недоступен. Проверьте соединение и попробуйте снова.");
    }

    private static String defaultMessage(int status) {
        return switch (status) {
            case 400 -> "Проверьте правильность заполнения полей.";
            case 401 -> "Неверное имя пользователя или пароль.";
            case 403 -> "Недостаточно прав для выполнения операции.";
            case 404 -> "Запрошенные данные не найдены.";
            case 409 -> "Пользователь с таким именем уже существует.";
            case 413 -> "Отправленные данные слишком велики.";
            case 429 -> "Слишком много запросов. Попробуйте позже.";
            case 500, 502, 503, 504 -> "Сервис временно недоступен. Попробуйте позже.";
            default -> "Не удалось выполнить запрос к API (HTTP " + status + ").";
        };
    }

    private static List<String> extractDetails(String responseBody, ObjectMapper objectMapper) {
        if (responseBody == null || responseBody.isBlank()) {
            return List.of();
        }

        List<String> details = new ArrayList<>();
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            collectTextArray(root.get("details"), details);
            collectTextArray(root.get("errors"), details);
        } catch (Exception ignored) {
            return List.of();
        }
        return List.copyOf(details);
    }

    private static void collectTextArray(JsonNode node, List<String> details) {
        if (node == null || !node.isArray()) {
            return;
        }
        for (JsonNode item : node) {
            if (details.size() >= MAX_DETAILS) {
                return;
            }
            if (item.isTextual()) {
                String text = item.asText().replaceAll("\\s+", " ").trim();
                if (!text.isEmpty()) {
                    details.add(text.length() > MAX_DETAIL_LENGTH
                            ? text.substring(0, MAX_DETAIL_LENGTH)
                            : text);
                }
            }
        }
    }
}
