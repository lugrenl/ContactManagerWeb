package org.example.web.service;

import org.example.web.client.ApiException;
import org.example.web.client.ContactApiClient;
import org.example.web.client.UserApiClient;
import org.example.web.dto.AdminContactDto;
import org.example.web.dto.AdminContactRow;
import org.example.web.dto.AdminUserForm;
import org.example.web.dto.UserDto;
import org.example.web.dto.UserUpdateRequest;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class AdminService {

    private static final Comparator<UserDto> USER_ORDER =
            Comparator.comparing(UserDto::getName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
                    .thenComparingLong(UserDto::getId);

    private static final Comparator<AdminContactDto> ADMIN_CONTACT_ORDER =
            Comparator.comparingLong(AdminContactDto::getOwnerId)
                    .thenComparing(AdminContactDto::getSurname,
                            Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
                    .thenComparing(AdminContactDto::getName,
                            Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
                    .thenComparingLong(AdminContactDto::getId);

    private final UserApiClient userApiClient;
    private final ContactApiClient contactApiClient;

    public AdminService(UserApiClient userApiClient, ContactApiClient contactApiClient) {
        this.userApiClient = userApiClient;
        this.contactApiClient = contactApiClient;
    }

    public List<UserDto> listUsers(String token) {
        return userApiClient.list(token).stream()
                .filter(Objects::nonNull)
                .sorted(USER_ORDER)
                .toList();
    }

    public UserDto getUser(String token, long id) {
        UserDto user = userApiClient.get(token, id);
        verifyId(user, id);
        return user;
    }

    public UserDto updateUser(String token, long id, AdminUserForm form) {
        UserUpdateRequest request = new UserUpdateRequest(
                trim(form.getName()),
                form.getPassword(),
                trim(form.getEmail()),
                blankToNull(form.getRole()));
        UserDto user = userApiClient.update(token, id, request);
        verifyId(user, id);
        return user;
    }

    public void deleteUser(String token, long id) {
        userApiClient.delete(token, id);
    }

    public boolean isCurrentUser(String token, String currentUsername, long id) {
        if (currentUsername == null || currentUsername.isBlank()) {
            return false;
        }
        return userApiClient.list(token).stream()
                .anyMatch(user -> user.getId() == id && currentUsername.equals(user.getName()));
    }

    public List<AdminContactRow> listAllContacts(String token) {
        List<UserDto> users = userApiClient.list(token);
        Map<Long, String> ownerNames = users.stream()
                .filter(user -> user.getName() != null)
                .collect(Collectors.toMap(UserDto::getId, UserDto::getName, (first, second) -> first));

        return contactApiClient.listAllContacts(token).stream()
                .filter(Objects::nonNull)
                .sorted(ADMIN_CONTACT_ORDER)
                .map(contact -> new AdminContactRow(contact, ownerNames.get(contact.getOwnerId())))
                .toList();
    }

    private static void verifyId(UserDto user, long expectedId) {
        if (user.getId() != expectedId) {
            throw new ApiException(502,
                    "Сервер вернул пользователя с неожиданным идентификатором. Операция отменена.");
        }
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
