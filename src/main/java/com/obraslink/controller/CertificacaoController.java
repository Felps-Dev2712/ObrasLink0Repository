package com.obraslink.controller;

import com.obraslink.model.Certificacao;
import com.obraslink.service.CertificacaoService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.*;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.nio.file.Files;
import java.time.LocalDate;

@Controller
@RequiredArgsConstructor
public class CertificacaoController {
    private final CertificacaoService service;

    @GetMapping("/prestadores/minhas-certificacoes")
    public String minhas(Model model) {
        model.addAttribute("certificacoes", service.minhas());
        model.addAttribute("dataMaximaEmissao", LocalDate.now());
        return "prestadores/certificacoes";
    }

    @GetMapping("/prestadores/minhas-certificacoes/{id}/editar")
    public String editar(@PathVariable Long id, Model model, RedirectAttributes attrs) {
        try {
            model.addAttribute("certificacao", service.minhaParaEdicao(id));
            model.addAttribute("dataMaximaEmissao", LocalDate.now());
            return "prestadores/certificacoes-editar";
        } catch (IllegalArgumentException e) {
            attrs.addFlashAttribute("mensagemErro", e.getMessage());
            return "redirect:/prestadores/minhas-certificacoes";
        }
    }

    @PostMapping("/prestadores/minhas-certificacoes")
    public String criar(@RequestParam String nome, @RequestParam String emissor,
                        @RequestParam String dataEmissao,
                        @RequestParam(required = false) String dataValidade,
                        @RequestParam(required = false) String codigoRegistro,
                        @RequestParam MultipartFile documento, RedirectAttributes attrs) {
        try {
            service.criar(nome, emissor, data(dataEmissao, true), data(dataValidade, false), codigoRegistro, documento);
            attrs.addFlashAttribute("mensagem", "Certificação enviada para validação.");
        } catch (IllegalArgumentException e) {
            attrs.addFlashAttribute("mensagemErro", e.getMessage());
        }
        return "redirect:/prestadores/minhas-certificacoes";
    }

    @PostMapping("/prestadores/minhas-certificacoes/{id}/editar")
    public String atualizar(@PathVariable Long id, @RequestParam String nome,
                            @RequestParam String emissor, @RequestParam String dataEmissao,
                            @RequestParam(required = false) String dataValidade,
                            @RequestParam(required = false) String codigoRegistro,
                            @RequestParam(required = false) MultipartFile documento,
                            RedirectAttributes attrs) {
        try {
            service.editar(id, nome, emissor, data(dataEmissao, true), data(dataValidade, false), codigoRegistro, documento);
            attrs.addFlashAttribute("mensagem", "Certificação atualizada e enviada para nova análise.");
        } catch (IllegalArgumentException e) {
            attrs.addFlashAttribute("mensagemErro", e.getMessage());
        }
        return "redirect:/prestadores/minhas-certificacoes";
    }

    @GetMapping("/admin/certificacoes")
    public String pendentes(Model model) {
        model.addAttribute("certificacoes", service.pendentes());
        return "admin/certificacoes";
    }

    @GetMapping("/certificacoes/{id}/documento")
    @ResponseBody
    public ResponseEntity<FileSystemResource> documento(@PathVariable Long id) {
        var c = service.consultarDocumento(id);
        var file = service.arquivo(c);
        if (!Files.isRegularFile(file)) return ResponseEntity.notFound().build();
        MediaType type = c.getDocumentoChave().endsWith(".pdf") ? MediaType.APPLICATION_PDF
                : c.getDocumentoChave().endsWith(".png") ? MediaType.IMAGE_PNG
                : c.getDocumentoChave().endsWith(".webp") ? MediaType.parseMediaType("image/webp") : MediaType.IMAGE_JPEG;
        return ResponseEntity.ok().contentType(type)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(c.getDocumentoNome()).build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(new FileSystemResource(file));
    }

    @PostMapping("/admin/certificacoes/{id}/validacao")
    public String validar(@PathVariable Long id, @RequestParam Certificacao.Estado estado,
                          @RequestParam(required = false) String observacao, RedirectAttributes attrs) {
        try {
            service.validar(id, estado, observacao);
            attrs.addFlashAttribute("mensagem", "Estado da certificação atualizado.");
        } catch (IllegalArgumentException e) {
            attrs.addFlashAttribute("mensagemErro", e.getMessage());
        }
        return "redirect:/admin/certificacoes";
    }

    private LocalDate data(String valor, boolean obrigatoria) {
        if (valor == null || valor.isBlank()) {
            if (obrigatoria) throw new IllegalArgumentException("Informe a data de emissão.");
            return null;
        }
        try {
            return LocalDate.parse(valor);
        } catch (java.time.format.DateTimeParseException e) {
            throw new IllegalArgumentException("Informe uma data válida.");
        }
    }
}
