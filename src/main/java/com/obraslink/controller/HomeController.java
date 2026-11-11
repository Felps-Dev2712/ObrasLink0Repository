package com.obraslink.controller;

import com.obraslink.repository.ClienteRepository;
import com.obraslink.repository.PrestadorRepository;
import com.obraslink.repository.ServicoRepository;
import com.obraslink.service.CategoriaService;
import com.obraslink.service.DestaquesService;
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
    private final CategoriaService categoriaService;
    private final DestaquesService destaquesService;
    private final com.obraslink.service.IndicadoresPlataformaService indicadores;
    private final com.obraslink.service.RelacionamentosService relacionamentosService;

    @GetMapping("/")
    public String home(Model model) {
        model.addAttribute("categorias", categoriaService.findAll());
        model.addAttribute("destaques", destaquesService.listar());
        model.addAttribute("favoritosPrestadores", relacionamentosService.idsFavoritosDoAtual());
        model.addAttribute("indicadores", indicadores.carregar());
        model.addAttribute("pageTitle", "ObraLink — Plataforma de Serviços de Construção Civil");
        return "index";
    }
}
