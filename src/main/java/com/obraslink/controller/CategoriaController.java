package com.obraslink.controller;
import com.obraslink.service.CategoriaService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
@Controller @RequiredArgsConstructor public class CategoriaController {
 private final CategoriaService categorias;
 @GetMapping("/categorias") public String lista(Model model){model.addAttribute("categorias",categorias.findAll());return "categorias/lista";}
}
