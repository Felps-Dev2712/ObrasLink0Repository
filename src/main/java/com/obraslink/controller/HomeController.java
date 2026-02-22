package com.obraslink.controller;

import com.obraslink.repository.ClienteRepository;
import com.obraslink.repository.PrestadorRepository;
import com.obraslink.repository.ServicoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@RequiredArgsConstructor
public class HomeController {

    private final PrestadorRepository prestadorRepository;
    private final ClienteRepository clienteRepository;
    private final ServicoRepository servicoRepository;

    @GetMapping("/")
    public String home(Model model) {
        // Actual stats for the hero and stats sections
        model.addAttribute("totalPrestadores", prestadorRepository.count());
        model.addAttribute("totalClientes", clienteRepository.count());
        model.addAttribute("totalServicos", servicoRepository.count());
        model.addAttribute("pageTitle", "Obras Link — Plataforma de Serviços de Construção Civil");
        return "index";
    }
}
