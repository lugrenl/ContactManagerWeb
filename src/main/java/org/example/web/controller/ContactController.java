package org.example.web.controller;

import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.example.web.client.ApiException;
import org.example.web.dto.ContactDto;
import org.example.web.dto.ContactForm;
import org.example.web.service.ContactService;
import org.example.web.session.SessionTokenStore;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/contacts")
public class ContactController {

    private static final long MAX_CSV_FILE_SIZE = 1024L * 1024L;

    private final ContactService contactService;
    private final SessionTokenStore tokenStore;

    public ContactController(ContactService contactService, SessionTokenStore tokenStore) {
        this.contactService = contactService;
        this.tokenStore = tokenStore;
    }

    @GetMapping
    public String list(@RequestParam(name = "importSuccess", defaultValue = "false") boolean importSuccess,
                       HttpSession session,
                       Model model) {
        model.addAttribute("contacts", contactService.list(token(session)));
        if (importSuccess) {
            model.addAttribute("infoMessage", "Импорт CSV завершён.");
        }
        return "contacts/list";
    }

    @GetMapping("/import")
    public String importForm(CsrfToken csrfToken,
                             @RequestParam(name = "importError", required = false) String importError,
                             Model model) {
        model.addAttribute("csrfHeaderName", csrfToken.getHeaderName());
        model.addAttribute("csrfTokenValue", csrfToken.getToken());
        model.addAttribute("maxFileSizeBytes", MAX_CSV_FILE_SIZE);
        if (importError != null) {
            model.addAttribute("errorMessage", importErrorMessage(importError));
        }
        return "contacts/import";
    }

    @PostMapping("/import")
    public String importCsv(@RequestPart(value = "file", required = false) MultipartFile file,
                            HttpSession session) {
        if (file == null || file.isEmpty()) {
            return "redirect:/contacts/import?importError=empty";
        }
        if (file.getSize() > MAX_CSV_FILE_SIZE) {
            return "redirect:/contacts/import?importError=size";
        }

        try {
            contactService.importCsv(token(session), file);
            return "redirect:/contacts?importSuccess=true";
        } catch (ApiException exception) {
            if (exception.getStatus() == 401) {
                throw exception;
            }
            return "redirect:/contacts/import?importError=" + importErrorCode(exception.getStatus());
        }
    }

    @GetMapping("/new")
    public String newForm(Model model) {
        model.addAttribute("contactForm", new ContactForm());
        prepareForm(model, "Новый контакт", "/contacts", null);
        return "contacts/form";
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("contactForm") ContactForm form,
                         BindingResult bindingResult,
                         HttpSession session,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        prepareForm(model, "Новый контакт", "/contacts", null);
        if (bindingResult.hasErrors()) {
            return "contacts/form";
        }

        try {
            contactService.create(token(session), form);
            redirectAttributes.addFlashAttribute("infoMessage", "Контакт сохранён.");
            return "redirect:/contacts";
        } catch (ApiException exception) {
            if (exception.getStatus() == 401) {
                throw exception;
            }
            return formError(model, exception);
        }
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable long id, HttpSession session, Model model) {
        ContactDto contact = contactService.get(token(session), id);
        ContactForm form = new ContactForm();
        form.setName(contact.getName());
        form.setSurname(contact.getSurname());
        form.setEmail(contact.getEmail());
        form.setPhoneNumber(contact.getPhoneNumber());

        model.addAttribute("contactForm", form);
        prepareForm(model, "Редактирование контакта", "/contacts/" + id + "/edit", id);
        return "contacts/form";
    }

    @PostMapping("/{id}/edit")
    public String update(@PathVariable long id,
                         @Valid @ModelAttribute("contactForm") ContactForm form,
                         BindingResult bindingResult,
                         HttpSession session,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        prepareForm(model, "Редактирование контакта", "/contacts/" + id + "/edit", id);
        if (bindingResult.hasErrors()) {
            return "contacts/form";
        }

        try {
            contactService.update(token(session), id, form);
            redirectAttributes.addFlashAttribute("infoMessage", "Контакт обновлён.");
            return "redirect:/contacts";
        } catch (ApiException exception) {
            if (exception.getStatus() == 401) {
                throw exception;
            }
            return formError(model, exception);
        }
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable long id, HttpSession session, RedirectAttributes redirectAttributes) {
        try {
            contactService.delete(token(session), id);
            redirectAttributes.addFlashAttribute("infoMessage", "Контакт удалён.");
        } catch (ApiException exception) {
            if (exception.getStatus() == 401) {
                throw exception;
            }
            redirectAttributes.addFlashAttribute("errorMessage", exception.getMessage());
        }
        return "redirect:/contacts";
    }

    private String token(HttpSession session) {
        return tokenStore.getToken(session)
                .orElseThrow(() -> new ApiException(401, "Сессия недействительна. Войдите снова."));
    }

    private static void prepareForm(Model model, String title, String action, Long contactId) {
        model.addAttribute("formTitle", title);
        model.addAttribute("formAction", action);
        model.addAttribute("contactId", contactId);
    }

    private static String formError(Model model, ApiException exception) {
        model.addAttribute("errorMessage", exception.getMessage());
        if (!exception.getDetails().isEmpty()) {
            model.addAttribute("errorDetails", exception.getDetails());
        }
        return "contacts/form";
    }

    private static String importErrorCode(int status) {
        return switch (status) {
            case 400 -> "invalid";
            case 403 -> "forbidden";
            case 413 -> "size";
            case 0, 500, 502, 503, 504 -> "unavailable";
            default -> "unknown";
        };
    }

    private static String importErrorMessage(String code) {
        return switch (code) {
            case "empty" -> "Выберите непустой CSV-файл.";
            case "invalid" -> "Не удалось импортировать CSV. Проверьте формат файла и данные.";
            case "size" -> "Файл превышает допустимый размер. Максимум — 1 MB.";
            case "forbidden" -> "Недостаточно прав для импорта контактов.";
            case "unavailable" -> "Сервер API недоступен. Попробуйте позже.";
            default -> "Не удалось выполнить импорт CSV. Попробуйте снова.";
        };
    }
}
