package com.gestordecontactos.controller;

import com.gestordecontactos.security.UsuarioPrincipal;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {

    @GetMapping("/")
    public String index(Model model, Authentication authentication) {
        model.addAttribute("autenticado", authentication != null
                && authentication.getPrincipal() instanceof UsuarioPrincipal);
        return "index";
    }

    @GetMapping("/login")
    public String login() {
        return "login";
    }
}
