package com.obraslink.controller;

import com.obraslink.service.ImagemService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Controller
@RequiredArgsConstructor
public class ImagemController {
    private final ImagemService imagens;

    /** Exibe uma imagem enviada. Só aceita chaves geradas pelo sistema (nome aleatório + extensão). */
    @GetMapping("/imagens/{chave}")
    @ResponseBody
    public ResponseEntity<FileSystemResource> exibir(@PathVariable String chave) {
        return imagens.arquivo(chave)
                .map(caminho -> ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(ImagemService.tipoMime(chave)))
                        .cacheControl(CacheControl.maxAge(7, TimeUnit.DAYS).cachePrivate())
                        .body(new FileSystemResource(caminho)))
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/prestadores/minha-conta/{tipo:foto|banner}")
    public String enviarImagemPerfil(@PathVariable String tipo, @RequestParam("arquivo") MultipartFile arquivo,
                                     @RequestParam(required = false) String retorno, RedirectAttributes attrs) {
        try {
            imagens.atualizarImagemPerfil(tipo(tipo), arquivo);
            attrs.addFlashAttribute("mensagem", "foto".equals(tipo) ? "Foto de perfil atualizada." : "Capa do perfil atualizada.");
        } catch (IllegalArgumentException e) {
            attrs.addFlashAttribute("mensagemErro", e.getMessage());
        }
        return "redirect:" + retornoSeguro(retorno);
    }

    @PostMapping("/prestadores/minha-conta/{tipo:foto|banner}/remover")
    public String removerImagemPerfil(@PathVariable String tipo, @RequestParam(required = false) String retorno,
                                      RedirectAttributes attrs) {
        try {
            imagens.removerImagemPerfil(tipo(tipo));
            attrs.addFlashAttribute("mensagem", "foto".equals(tipo) ? "Foto de perfil removida." : "Capa do perfil removida.");
        } catch (IllegalArgumentException e) {
            attrs.addFlashAttribute("mensagemErro", e.getMessage());
        }
        return "redirect:" + retornoSeguro(retorno);
    }

    @PostMapping("/clientes/minha-conta/foto")
    public String enviarFotoCliente(@RequestParam("arquivo") MultipartFile arquivo, RedirectAttributes attrs) {
        try {
            imagens.atualizarFotoCliente(arquivo);
            attrs.addFlashAttribute("mensagem", "Foto de perfil atualizada.");
        } catch (IllegalArgumentException e) {
            attrs.addFlashAttribute("mensagemErro", e.getMessage());
        }
        return "redirect:/clientes";
    }

    @PostMapping("/clientes/minha-conta/foto/remover")
    public String removerFotoCliente(RedirectAttributes attrs) {
        try {
            imagens.removerFotoCliente();
            attrs.addFlashAttribute("mensagem", "Foto de perfil removida.");
        } catch (IllegalArgumentException e) {
            attrs.addFlashAttribute("mensagemErro", e.getMessage());
        }
        return "redirect:/clientes";
    }

    @PostMapping("/servicos/{id}/fotos")
    public String enviarFotosServico(@PathVariable Long id, @RequestParam(value = "fotos", required = false) List<MultipartFile> fotos,
                                     RedirectAttributes attrs) {
        try {
            int total = imagens.adicionarFotosServico(id, fotos);
            if (total == 0) attrs.addFlashAttribute("mensagemErro", "Selecione ao menos uma foto.");
            else attrs.addFlashAttribute("mensagem", total == 1 ? "Foto adicionada." : total + " fotos adicionadas.");
        } catch (IllegalArgumentException e) {
            attrs.addFlashAttribute("mensagemErro", e.getMessage());
        }
        return "redirect:/servicos/" + id + "/editar";
    }

    @PostMapping("/servicos/{id}/fotos/{fotoId}/excluir")
    public String excluirFotoServico(@PathVariable Long id, @PathVariable Long fotoId, RedirectAttributes attrs) {
        try {
            imagens.excluirFotoServico(id, fotoId);
            attrs.addFlashAttribute("mensagem", "Foto removida.");
        } catch (IllegalArgumentException e) {
            attrs.addFlashAttribute("mensagemErro", e.getMessage());
        }
        return "redirect:/servicos/" + id + "/editar";
    }

    private ImagemService.Tipo tipo(String tipo) {
        return "foto".equals(tipo) ? ImagemService.Tipo.FOTO_PERFIL : ImagemService.Tipo.BANNER;
    }

    /** Volta só para telas conhecidas (evita redirecionamento aberto). */
    static String retornoSeguro(String retorno) {
        if (retorno != null && (retorno.equals("/clientes") || retorno.matches("/prestadores/[0-9]{1,18}"))) return retorno;
        return "/clientes";
    }
}
