package com.obraslink.controller;

import com.obraslink.model.Disponibilidade;
import com.obraslink.service.RelacionamentosService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.http.ResponseEntity;
import java.util.List;
import java.util.Map;

@Controller @RequiredArgsConstructor
public class RelacionamentosController {
 private final RelacionamentosService service;
 @GetMapping("/favoritos") public String favoritos(Model model){model.addAttribute("favoritos",service.favoritosDoAtual());return "clientes/favoritos";}
 @PostMapping("/favoritos/{prestadorId}") public String favorito(@PathVariable Long prestadorId,@RequestParam boolean adicionar,@RequestParam(required=false) String retorno,RedirectAttributes attrs){try{service.alternarFavorito(prestadorId,adicionar);attrs.addFlashAttribute("mensagem","Favoritos atualizados.");}catch(IllegalArgumentException e){attrs.addFlashAttribute("mensagemErro",e.getMessage());}return "redirect:"+retornoSeguro(retorno,adicionar?"/prestadores/"+prestadorId:"/favoritos");}
 @PostMapping(value="/favoritos/{prestadorId}/alternar", produces="application/json") @ResponseBody public ResponseEntity<Map<String,Object>> favoritoAssincrono(@PathVariable Long prestadorId,@RequestParam boolean adicionar){try{service.alternarFavorito(prestadorId,adicionar);return ResponseEntity.ok(Map.of("favoritado",adicionar,"mensagem",adicionar?"Prestador adicionado aos favoritos.":"Prestador removido dos favoritos."));}catch(IllegalArgumentException e){return ResponseEntity.badRequest().body(Map.of("mensagem",e.getMessage()));}}
 private String retornoSeguro(String retorno,String padrao){if(retorno==null||!retorno.startsWith("/")||retorno.startsWith("//")||retorno.contains("\\")||retorno.contains("\r")||retorno.contains("\n"))return padrao;if(retorno.equals("/")||retorno.equals("/favoritos")||retorno.matches("/vitrine(?:\\?.*)?")||retorno.matches("/prestadores/[0-9]+(?:\\?.*)?"))return retorno;return padrao;}
 @GetMapping("/prestadores/minha-disponibilidade") public String disponibilidade(Model model){model.addAttribute("disponibilidades",service.disponibilidadeAtual());model.addAttribute("dias",Disponibilidade.DiaSemana.values());return "prestadores/disponibilidade";}
 @PostMapping("/prestadores/minha-disponibilidade") public String disponibilidade(@ModelAttribute("itens") DisponibilidadeForm form,RedirectAttributes attrs){try{service.substituirDisponibilidade(form.getItens());attrs.addFlashAttribute("mensagem","Disponibilidade atualizada.");}catch(IllegalArgumentException e){attrs.addFlashAttribute("mensagemErro",e.getMessage());}return "redirect:/prestadores/minha-disponibilidade";}
 public static class DisponibilidadeForm {private List<Disponibilidade> itens;public List<Disponibilidade> getItens(){return itens;}public void setItens(List<Disponibilidade> itens){this.itens=itens;}}
}
