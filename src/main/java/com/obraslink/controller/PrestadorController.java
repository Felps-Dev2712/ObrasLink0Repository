package com.obraslink.controller;

import com.obraslink.dto.AvaliacaoPrestadorForm;
import com.obraslink.model.AvaliacaoPrestador;
import com.obraslink.model.PapelUsuario;
import com.obraslink.model.Prestador;
import com.obraslink.model.Usuario;
import com.obraslink.service.AcessoService;
import com.obraslink.service.AvaliacaoPrestadorService;
import com.obraslink.service.CategoriaService;
import com.obraslink.service.PrestadorService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.Optional;

@Controller
@RequestMapping("/prestadores")
@RequiredArgsConstructor
public class PrestadorController {

    private final PrestadorService prestadorService;
    private final CategoriaService categoriaService;
    private final AcessoService acessoService;
    private final AvaliacaoPrestadorService avaliacaoPrestadorService;
    @org.springframework.beans.factory.annotation.Autowired(required=false)
    private com.obraslink.service.ServicoService servicoService;
    @org.springframework.beans.factory.annotation.Autowired(required=false)
    private com.obraslink.service.RelacionamentosService relacionamentosService;
    @org.springframework.beans.factory.annotation.Autowired(required=false)
    private com.obraslink.service.PerfilAtivoService perfilAtivoService;

    private boolean podeGerenciarPrestador(Long id){return acessoService.isAdmin()||(perfilAtivoService==null||perfilAtivoService.atual()==PapelUsuario.PRESTADOR)&&acessoService.podeGerenciarPrestador(id);}

    @GetMapping
    public String list(@RequestParam(required = false) Long categoriaId, Model model) {
        Optional<Usuario> usuarioOpt = acessoService.getUsuarioAtual();
        boolean admin = usuarioOpt.map(acessoService::isAdmin).orElse(false);
        Long usuarioAtualId = usuarioOpt.map(Usuario::getId).orElse(null);

        model.addAttribute("prestadores", categoriaId == null
                ? prestadorService.findAllActive() : prestadorService.findByCategoria(categoriaId));
        model.addAttribute("categorias", categoriaService.findAll());
        model.addAttribute("categoriaAtiva", categoriaId);
        model.addAttribute("isAdmin", admin);
        model.addAttribute("usuarioAtualId", usuarioAtualId);
        return "prestadores/lista";
    }

    @GetMapping("/novo")
    public String createForm(Model model, RedirectAttributes redirectAttributes) {
        if (!acessoService.isAdmin()) {
            redirectAttributes.addFlashAttribute("mensagemErro", "Apenas administradores podem criar prestadores por esta tela.");
            return "redirect:/prestadores";
        }

        model.addAttribute("prestador", new Prestador());
        model.addAttribute("categorias", categoriaService.findAll());
        model.addAttribute("isAdmin", true);
        return "prestadores/form";
    }

    @PostMapping
    public String save(@ModelAttribute Prestador prestador, RedirectAttributes redirectAttributes) {
        if (!acessoService.isAdmin()) {
            redirectAttributes.addFlashAttribute("mensagemErro", "Apenas administradores podem criar prestadores por esta tela.");
            return "redirect:/prestadores";
        }

        prestadorService.save(prestador);
        redirectAttributes.addFlashAttribute("mensagem", "Prestador salvo com sucesso!");
        return "redirect:/prestadores";
    }

    @GetMapping("/{id}")
    public String view(@PathVariable Long id, Model model) {
        return prestadorService.findPerfilPublico(id).map(prestador -> {
            List<AvaliacaoPrestador> avaliacoes = avaliacaoPrestadorService.findByPrestador(prestador.getId());
            var atendimentosParaAvaliar = avaliacaoPrestadorService.atendimentosDisponiveis(prestador.getId());

            model.addAttribute("prestador", prestador);
            model.addAttribute("podeGerenciarPrestador", podeGerenciarPrestador(prestador.getId()));
            model.addAttribute("podeAvaliarPrestador", !atendimentosParaAvaliar.isEmpty());
            model.addAttribute("atendimentosParaAvaliar", atendimentosParaAvaliar);
            if (!model.containsAttribute("avaliacaoForm")) model.addAttribute("avaliacaoForm", new AvaliacaoPrestadorForm());
            model.addAttribute("avaliacoes", avaliacoes);
            model.addAttribute("mediaAvaliacoes", avaliacaoPrestadorService.calcularMediaGeral(avaliacoes));
            model.addAttribute("totalAvaliacoes", avaliacoes.size());
            model.addAttribute("servicos",servicoService==null?List.of():servicoService.findByPrestador(prestador.getId()));
            model.addAttribute("disponibilidades",relacionamentosService==null?List.of():relacionamentosService.disponibilidade(prestador.getId()));
            Long clienteId=acessoService.getUsuarioAtual().flatMap(u->u.getPapeis().contains(PapelUsuario.CLIENTE)?java.util.Optional.ofNullable(u.getId()):java.util.Optional.<Long>empty()).orElse(null);
            model.addAttribute("favorito",clienteId!=null&&relacionamentosService!=null&&relacionamentosService.ehFavorito(clienteId,prestador.getId()));
            model.addAttribute("perfilAtivo",perfilAtivoService==null||perfilAtivoService.atual()==null?"ADMIN":perfilAtivoService.atual().name());
            return "prestadores/detalhes";
        }).orElse("redirect:/prestadores");
    }

    @PostMapping("/{id}/atendimentos/{atendimentoId}/avaliacoes")
    public String avaliarAtendimento(@PathVariable Long id,@PathVariable Long atendimentoId,
            @ModelAttribute AvaliacaoPrestadorForm form,RedirectAttributes redirectAttributes){
        try{avaliacaoPrestadorService.avaliar(id,atendimentoId,form);redirectAttributes.addFlashAttribute("mensagem","Avaliação registrada com sucesso!");}
        catch(IllegalArgumentException ex){redirectAttributes.addFlashAttribute("mensagemErro",ex.getMessage());redirectAttributes.addFlashAttribute("avaliacaoForm",form);}
        return "redirect:/prestadores/"+id+"#avaliacoes";
    }

    @GetMapping("/{id}/editar")
    public String editForm(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        if (!podeGerenciarPrestador(id)) {
            redirectAttributes.addFlashAttribute("mensagemErro", "Voce nao tem permissao para editar este prestador.");
            return "redirect:/prestadores";
        }

        return prestadorService.findById(id).map(prestador -> {
            model.addAttribute("prestador", prestador);
            model.addAttribute("categorias", categoriaService.findAll());
            model.addAttribute("isAdmin", acessoService.isAdmin());
            return "prestadores/form";
        }).orElse("redirect:/prestadores");
    }

    @PostMapping("/minha-conta")
    public String updateOwnProfile(@ModelAttribute Prestador dados,
                                   RedirectAttributes redirectAttributes) {
        if(perfilAtivoService!=null&&perfilAtivoService.atual()!=PapelUsuario.PRESTADOR){redirectAttributes.addFlashAttribute("mensagemErro","Ative o perfil de prestador para editar esses dados.");return "redirect:/clientes";}
        Usuario usuario = acessoService.getUsuarioAtualOrThrow();
        Prestador prestador = prestadorService.findByUsuarioId(usuario.getId()).orElse(null);
        if (prestador == null) {
            redirectAttributes.addFlashAttribute("mensagemErro", "Seu perfil profissional não foi encontrado.");
            return "redirect:/clientes";
        }

        prestador.setNome(dados.getNome());
        prestador.setCpfCnpj(dados.getCpfCnpj());
        prestador.setWhatsapp(dados.getWhatsapp());
        prestador.setDescricao(dados.getDescricao());
        prestador.setCategoria(dados.getCategoria());
        prestador.setAnosExperiencia(dados.getAnosExperiencia());
        prestador.setPrecoMinimo(dados.getPrecoMinimo());
        prestadorService.save(prestador);
        redirectAttributes.addFlashAttribute("mensagem", "Dados profissionais atualizados com sucesso!");
        return "redirect:/clientes";
    }

    @PostMapping("/{id}")
    public String update(@PathVariable Long id,
                         @ModelAttribute Prestador prestador,
                         RedirectAttributes redirectAttributes) {
        if (!podeGerenciarPrestador(id)) {
            redirectAttributes.addFlashAttribute("mensagemErro", "Voce nao tem permissao para editar este prestador.");
            return "redirect:/prestadores";
        }

        Prestador existente = prestadorService.findById(id).orElse(null);
        if (existente == null) {
            redirectAttributes.addFlashAttribute("mensagemErro", "Prestador nao encontrado.");
            return "redirect:/prestadores";
        }

        prestador.setId(id);
        prestador.setUsuarioId(existente.getUsuarioId());
        prestador.setCategorias(existente.getCategorias() == null
                ? new java.util.ArrayList<>()
                : new java.util.ArrayList<>(existente.getCategorias()));
        if (!acessoService.isAdmin()) {
            prestador.setEmail(existente.getEmail());
        }

        prestadorService.save(prestador);
        redirectAttributes.addFlashAttribute("mensagem", "Prestador atualizado com sucesso!");
        return "redirect:/prestadores";
    }

    @PostMapping("/{id}/excluir")
    public String delete(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        if (!podeGerenciarPrestador(id)) {
            redirectAttributes.addFlashAttribute("mensagemErro", "Voce nao tem permissao para excluir este prestador.");
            return "redirect:/prestadores";
        }

        prestadorService.delete(id);
        redirectAttributes.addFlashAttribute("mensagem", "Prestador excluido com sucesso!");
        return "redirect:/prestadores";
    }
}
