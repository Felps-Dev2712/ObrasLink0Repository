package com.obraslink.controller;

import com.obraslink.dto.OrcamentoFormularioRequest;
import com.obraslink.model.SolicitacaoOrcamento;
import com.obraslink.service.OrcamentoService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

/**
 * Pedido de orçamento a vários prestadores da mesma categoria, num só formulário: seleção na vitrine
 * (ver prestadores/lista.html) → formulário (geral + específico da categoria) → revisão (uma mensagem
 * por prestador, editável) → confirmação (abre o WhatsApp de cada um, individualmente).
 */
@Controller
@RequestMapping("/orcamentos")
@RequiredArgsConstructor
public class OrcamentoController {
    private final OrcamentoService orcamentoService;

    @GetMapping("/novo")
    public String iniciar(@RequestParam(required = false) List<Long> prestadorId, RedirectAttributes attributes) {
        try {
            Long id = orcamentoService.iniciar(prestadorId);
            return proximaEtapa(id, orcamentoService.consultarFormulario(id));
        } catch (IllegalArgumentException ex) {
            attributes.addFlashAttribute("mensagemErro", ex.getMessage());
            return "redirect:/prestadores";
        }
    }

    /** Quando os prestadores selecionados têm mais de uma especialidade em comum, escolhe-se antes do formulário. */
    @GetMapping("/{id}/especialidades")
    public String especialidades(@PathVariable Long id, Model model, RedirectAttributes attributes) {
        try {
            SolicitacaoOrcamento solicitacao = orcamentoService.consultarFormulario(id);
            if (!solicitacao.getCategoriasNomes().isEmpty()) return "redirect:/orcamentos/" + id + "/formulario";
            model.addAttribute("solicitacao", solicitacao);
            model.addAttribute("itens", orcamentoService.itensDaSolicitacao(id));
            model.addAttribute("comuns", orcamentoService.especialidadesComunsDaSolicitacao(id));
            return "orcamentos/especialidades";
        } catch (IllegalArgumentException ex) {
            attributes.addFlashAttribute("mensagemErro", ex.getMessage());
            return "redirect:/prestadores";
        }
    }

    @PostMapping("/{id}/especialidades")
    public String escolherEspecialidades(@PathVariable Long id, @RequestParam(required = false) List<Long> categoriaId,
                                          RedirectAttributes attributes) {
        try {
            orcamentoService.escolherEspecialidades(id, categoriaId);
            return "redirect:/orcamentos/" + id + "/formulario";
        } catch (IllegalArgumentException ex) {
            attributes.addFlashAttribute("mensagemErro", ex.getMessage());
            return "redirect:/orcamentos/" + id + "/especialidades";
        }
    }

    @GetMapping("/{id}/formulario")
    public String formulario(@PathVariable Long id, Model model, RedirectAttributes attributes) {
        try {
            SolicitacaoOrcamento solicitacao = orcamentoService.consultarFormulario(id);
            if (solicitacao.getCategoriasNomes().isEmpty()) return "redirect:/orcamentos/" + id + "/especialidades";
            carregarFormulario(id, solicitacao, model);
            return "orcamentos/formulario";
        } catch (IllegalArgumentException ex) {
            attributes.addFlashAttribute("mensagemErro", ex.getMessage());
            return "redirect:/prestadores";
        }
    }

    private String proximaEtapa(Long id, SolicitacaoOrcamento solicitacao) {
        return "redirect:/orcamentos/" + id + (solicitacao.getCategoriasNomes().isEmpty() ? "/especialidades" : "/formulario");
    }

    @PostMapping("/{id}/formulario")
    public String salvar(@PathVariable Long id, @ModelAttribute OrcamentoFormularioRequest form,
                          @RequestParam(required = false) List<MultipartFile> anexos,
                          Model model, RedirectAttributes attributes) {
        try {
            orcamentoService.salvarFormulario(id, form, anexos);
            return "redirect:/orcamentos/" + id + "/revisar";
        } catch (IllegalArgumentException ex) {
            attributes.addFlashAttribute("mensagemErro", ex.getMessage());
            return "redirect:/orcamentos/" + id + "/formulario";
        }
    }

    @PostMapping("/{id}/anexos/{anexoId}/excluir")
    public String excluirAnexo(@PathVariable Long id, @PathVariable Long anexoId, RedirectAttributes attributes) {
        try {
            orcamentoService.excluirAnexo(id, anexoId);
        } catch (IllegalArgumentException ex) {
            attributes.addFlashAttribute("mensagemErro", ex.getMessage());
        }
        return "redirect:/orcamentos/" + id + "/formulario";
    }

    @GetMapping("/{id}/revisar")
    public String revisar(@PathVariable Long id, Model model, RedirectAttributes attributes) {
        try {
            model.addAttribute("solicitacao", orcamentoService.consultarFormulario(id));
            model.addAttribute("itens", orcamentoService.itensDaSolicitacao(id));
            return "orcamentos/revisar";
        } catch (IllegalArgumentException ex) {
            attributes.addFlashAttribute("mensagemErro", ex.getMessage());
            return "redirect:/prestadores";
        }
    }

    /** A revisão envia um campo "mensagem_{itemId}" por prestador; salva os ajustes e manda tudo de uma vez. */
    @PostMapping("/{id}/confirmar")
    public String confirmar(@PathVariable Long id, @RequestParam java.util.Map<String, String> parametros,
                             Model model, RedirectAttributes attributes) {
        try {
            java.util.Map<Long, String> mensagens = new java.util.HashMap<>();
            parametros.forEach((chave, valor) -> {
                if (chave.startsWith("mensagem_")) {
                    mensagens.put(Long.valueOf(chave.substring("mensagem_".length())), valor);
                }
            });
            model.addAttribute("resultados", orcamentoService.confirmarEnvio(id, mensagens));
            return "orcamentos/enviado";
        } catch (IllegalArgumentException ex) {
            attributes.addFlashAttribute("mensagemErro", ex.getMessage());
            return "redirect:/orcamentos/" + id + "/revisar";
        }
    }

    private void carregarFormulario(Long id, SolicitacaoOrcamento solicitacao, Model model) {
        model.addAttribute("solicitacao", solicitacao);
        model.addAttribute("itens", orcamentoService.itensDaSolicitacao(id));
        model.addAttribute("anexos", orcamentoService.anexosDaSolicitacao(id));
        model.addAttribute("maxAnexos", com.obraslink.service.OrcamentoAnexoService.MAX_ANEXOS_POR_SOLICITACAO);
        model.addAttribute("urgencias", SolicitacaoOrcamento.Urgencia.values());
        model.addAttribute("enderecoCadastrado", orcamentoService.enderecoCadastradoAtual());
        if (!model.containsAttribute("form")) {
            OrcamentoFormularioRequest form = new OrcamentoFormularioRequest();
            // Já tem um endereço próprio desta solicitação (voltou para editar): mostra "usar outro" com esses dados.
            // Senão, parte do endereço do perfil como padrão, como combinado ("estilo iFood").
            form.setUsarEnderecoCadastrado(solicitacao.getEnderecoExecucao() == null);
            if (solicitacao.getEnderecoExecucao() != null) form.setEnderecoExecucao(solicitacao.getEnderecoExecucao());
            form.setDescricaoGeral(solicitacao.getDescricaoGeral());
            form.setDataPrevisao(solicitacao.getDataPrevisao());
            form.setUrgencia(solicitacao.getUrgencia());
            form.setInformacoesComplementares(solicitacao.getInformacoesComplementares());
            form.setCampos(solicitacao.getCamposEspecificos());
            model.addAttribute("form", form);
        }
    }
}
