package org.example.web.service;

import org.example.web.client.ApiException;
import org.example.web.client.ContactApiClient;
import org.example.web.dto.ContactDto;
import org.example.web.dto.ContactForm;
import org.example.web.dto.ContactRequest;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

@Service
public class ContactService {

    private static final Comparator<ContactDto> CONTACT_ORDER =
            Comparator.comparing(ContactDto::getSurname, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
                    .thenComparing(ContactDto::getName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
                    .thenComparingLong(ContactDto::getId);

    private final ContactApiClient contactApiClient;

    public ContactService(ContactApiClient contactApiClient) {
        this.contactApiClient = contactApiClient;
    }

    public List<ContactDto> list(String token) {
        return contactApiClient.list(token).stream()
                .filter(Objects::nonNull)
                .sorted(CONTACT_ORDER)
                .toList();
    }

    public ContactDto get(String token, long id) {
        ContactDto contact = contactApiClient.get(token, id);
        verifyId(contact, id);
        return contact;
    }

    public ContactDto create(String token, ContactForm form) {
        return contactApiClient.create(token, toRequest(form));
    }

    public ContactDto update(String token, long id, ContactForm form) {
        ContactDto contact = contactApiClient.update(token, id, toRequest(form));
        verifyId(contact, id);
        return contact;
    }

    public void delete(String token, long id) {
        contactApiClient.delete(token, id);
    }

    public void importCsv(String token, MultipartFile file) {
        contactApiClient.importCsv(token, file);
    }

    private static ContactRequest toRequest(ContactForm form) {
        return new ContactRequest(
                trim(form.getName()),
                trim(form.getSurname()),
                trimToNull(form.getEmail()),
                trimToNull(form.getPhoneNumber()));
    }

    private static void verifyId(ContactDto contact, long expectedId) {
        if (contact.getId() != expectedId) {
            throw new ApiException(502,
                    "Сервер вернул контакт с неожиданным идентификатором. Операция отменена.");
        }
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
