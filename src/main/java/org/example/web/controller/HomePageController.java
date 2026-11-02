package org.example.web.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class HomePageController {

    private final String apiBaseUrl;

    public HomePageController(@Value("${contactmanager.api.base-url}") String apiBaseUrl) {
        this.apiBaseUrl = apiBaseUrl;
    }

    @GetMapping("/")
    public String home(@RequestParam(name = "accessDenied", required = false) String accessDenied,
                       Model model) {
        model.addAttribute("apiBaseUrl", apiBaseUrl);
        if (accessDenied != null) {
            model.addAttribute("infoMessage",
                    "Недостаточно прав для доступа к административному разделу.");
        }
        return "home";
    }
}
