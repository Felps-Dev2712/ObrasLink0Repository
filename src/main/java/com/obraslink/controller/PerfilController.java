package com.obraslink.controller;
import com.obraslink.model.PapelUsuario;
import com.obraslink.service.PerfilAtivoService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
@Controller @RequiredArgsConstructor
public class PerfilController {
 private final PerfilAtivoService perfil;
 @PostMapping("/perfil/alternar") public String alternar(@RequestParam PapelUsuario papel,@RequestParam(defaultValue="/dashboard") String retorno,RedirectAttributes attrs){try{perfil.selecionar(papel);}catch(IllegalArgumentException e){attrs.addFlashAttribute("mensagemErro",e.getMessage());}if(retorno==null||!retorno.startsWith("/")||retorno.startsWith("//")||retorno.contains("\\"))return "redirect:/dashboard";return "redirect:"+retorno;}
}
