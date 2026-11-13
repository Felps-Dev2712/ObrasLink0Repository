package com.obraslink.controller;

import com.obraslink.model.PapelUsuario;
import com.obraslink.model.Prestador;
import com.obraslink.model.Servico;
import com.obraslink.model.Usuario;
import com.obraslink.service.AcessoService;
import com.obraslink.service.CategoriaService;
import com.obraslink.service.PrestadorService;
import com.obraslink.service.ServicoService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Collections;
import java.util.List;

@Controller
@RequiredArgsConstructor
public class ServicoController {

    private final ServicoService servicoService;
    private final PrestadorService prestadorService;
    private final CategoriaService categoriaService;
    private final AcessoService acessoService;
    private final com.obraslink.service.RelacionamentosService relacionamentosService;
    @org.springframework.beans.factory.annotation.Autowired(required=false) private com.obraslink.service.PerfilAtivoService perfilAtivo;
    @org.springframework.beans.factory.annotation.Autowired(required=false) private com.obraslink.service.ServicoDetalheService detalheService;
    @org.springframework.beans.factory.annotation.Autowired(required=false) private com.obraslink.service.ImagemService imagemService;

    /** Primeira foto de cada serviço (uma consulta só), para as listagens. */
    private java.util.Map<Long, String> capas(List<Servico> servicos) {
        if (imagemService == null || servicos == null || servicos.isEmpty()) return java.util.Map.of();
        var porServico = imagemService.fotosPorServico(servicos.stream().map(Servico::getId).toList());
        var capas = new java.util.HashMap<Long, String>();
        porServico.forEach((id, lista) -> { if (!lista.isEmpty()) capas.put(id, lista.get(0).getChave()); });
        return capas;
    }

    /** Anexa as fotos enviadas junto do formulário; um erro nas fotos não desfaz o serviço já salvo. */
    private void anexarFotos(Long servicoId, List<org.springframework.web.multipart.MultipartFile> fotos, RedirectAttributes attrs) {
        if (imagemService == null || fotos == null) return;
        try {
            imagemService.adicionarFotosServico(servicoId, fotos);
        } catch (IllegalArgumentException e) {
            attrs.addFlashAttribute("mensagemErro", "Serviço salvo, mas as fotos não foram enviadas: " + e.getMessage());
        }
    }

    private boolean podeGerenciarServico(Long id){return acessoService.isAdmin()||(perfilAtivo==null||perfilAtivo.atual()==PapelUsuario.PRESTADOR)&&acessoService.podeGerenciarServico(id);}

    /** Preço no formato brasileiro ("R$ 2.000,00") nos dois sentidos: exibição no formulário e leitura do envio. */
    @InitBinder("servico")
    void configurarPreco(org.springframework.web.bind.WebDataBinder binder) {
        binder.registerCustomEditor(java.math.BigDecimal.class, "preco", new com.obraslink.validation.Moeda.Editor());
    }

    /**
     * A categoria do serviço é sempre uma especialidade do PRÓPRIO prestador — nunca uma categoria de fora,
     * mesmo que alguém envie outro id por fora da tela. O prestador escolhe entre as especialidades do seu
     * perfil (principal + secundárias); o admin continua usando a especialidade principal do profissional
     * escolhido, sem lista própria (o formulário não recarrega as opções ao trocar o profissional).
     * Devolve a mensagem de erro, ou null se deu certo.
     */
    private String aplicarCategoriaDoPrestador(Servico servico, boolean admin) {
        Prestador dono = servico.getPrestador() == null || servico.getPrestador().getId() == null ? servico.getPrestador()
                : prestadorService.findById(servico.getPrestador().getId()).orElse(servico.getPrestador());
        if (dono == null) return "Selecione o profissional responsável.";
        servico.setPrestador(dono);
        List<com.obraslink.model.Categoria> especialidades = dono.getCategorias() == null ? List.of() : dono.getCategorias();
        if (especialidades.isEmpty() && dono.getCategoria() == null) {
            return "Defina sua especialidade principal em Minha conta antes de cadastrar serviços: ela é a categoria dos seus serviços.";
        }
        if (admin) {
            if (dono.getCategoria() == null) return "Defina a especialidade principal deste profissional antes de salvar o serviço.";
            servico.setCategoria(dono.getCategoria());
            return null;
        }
        Long escolhidaId = servico.getCategoria() == null ? null : servico.getCategoria().getId();
        com.obraslink.model.Categoria escolhida = especialidades.stream()
                .filter(c -> java.util.Objects.equals(c.getId(), escolhidaId)).findFirst()
                .orElse(dono.getCategoria()); // vazio ou id que não é do prestador: volta para a especialidade principal
        if (escolhida == null) return "Selecione uma das suas especialidades cadastradas em Minha conta.";
        servico.setCategoria(escolhida);
        return null;
    }

    @GetMapping("/vitrine")
    public String vitrine(@RequestParam(required = false) Long categoriaId, @RequestParam(required = false) String q, Model model) {
        List<Servico> servicos = servicoService.findByCategoria(categoriaId);
        String termoBusca = q == null || q.isBlank() ? null : q.trim();
        if (termoBusca != null) {
            String termoBuscaMinusculo = termoBusca.toLowerCase(java.util.Locale.ROOT);
            servicos = servicos.stream().filter(s -> contemTermo(s, termoBuscaMinusculo)).toList();
        }
        model.addAttribute("servicos", servicos);
        model.addAttribute("capasServicos", capas(servicos));
        model.addAttribute("categorias", categoriaService.findAll());
        model.addAttribute("categoriaAtiva", categoriaId);
        model.addAttribute("termoBusca", termoBusca);
        return "vitrine/index";
    }

    /** Busca livre da vitrine: título, descrição, categoria ou nome do profissional contendo o termo (sem diferenciar maiúsculas). */
    private boolean contemTermo(Servico servico, String termoBuscaMinusculo) {
        return contem(servico.getTitulo(), termoBuscaMinusculo) || contem(servico.getDescricao(), termoBuscaMinusculo)
                || contem(servico.getCategoria().getNome(), termoBuscaMinusculo) || contem(servico.getPrestador().getNome(), termoBuscaMinusculo);
    }

    private boolean contem(String campo, String termoBuscaMinusculo) {
        return campo != null && campo.toLowerCase(java.util.Locale.ROOT).contains(termoBuscaMinusculo);
    }

    @GetMapping("/servicos")
    public String list(@RequestParam(required=false) String disponibilidade, Model model) {
        Usuario usuario = acessoService.getUsuarioAtualOrThrow();
        boolean admin = acessoService.isAdmin(usuario);
        PapelUsuario ativo=perfilAtivo==null?null:perfilAtivo.atual();
        boolean cliente = usuario.getPapeis().contains(PapelUsuario.CLIENTE)&&(ativo==PapelUsuario.CLIENTE||ativo==null&&!usuario.getPapeis().contains(PapelUsuario.PRESTADOR));
        boolean prestador = usuario.getPapeis().contains(PapelUsuario.PRESTADOR)&&(ativo==PapelUsuario.PRESTADOR||ativo==null&&!usuario.getPapeis().contains(PapelUsuario.CLIENTE));

        Long prestadorAtualId = null;
        List<Servico> servicos;
        Prestador prestadorAtual = null;
        if (prestador) {
            prestadorAtual = prestadorService.findByUsuarioId(usuario.getId()).orElse(null);
            if (prestadorAtual != null) {
                prestadorAtualId = prestadorAtual.getId();
            }
        }

        if (admin) {
            servicos = servicoService.findAll();
        } else if (prestador && !cliente) {
            if (prestadorAtual == null) {
                servicos = Collections.emptyList();
            } else {
                servicos = servicoService.findByPrestador(prestadorAtualId);
            }
        } else {
            servicos = servicoService.findAllAvailable();
        }
        String filtro=disponibilidade==null?"todos":disponibilidade;
        if(!List.of("todos","disponiveis","indisponiveis").contains(filtro))filtro="todos";
        if (!admin && prestador && !cliente && prestadorAtualId!=null && !filtro.equals("todos")) {
            servicos=servicoService.findByPrestadorDisponibilidade(prestadorAtualId,filtro.equals("disponiveis"));
        }

        model.addAttribute("servicos", servicos);
        model.addAttribute("capasServicos", capas(servicos));
        model.addAttribute("isAdmin", admin);
        model.addAttribute("prestadorAtualId", prestadorAtualId);
        model.addAttribute("podeGerenciarServicos", admin || prestador);
        model.addAttribute("filtroDisponibilidade",filtro);
        return "servicos/lista";
    }

    /** Página pública de detalhes do serviço (qualquer usuário logado). Serviço inexistente ou de conta inativa volta para a vitrine. */
    @GetMapping("/servicos/{id:\\d+}")
    public String detalhes(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        var detalhe = detalheService == null ? java.util.Optional.<com.obraslink.service.ServicoDetalheService.Detalhe>empty() : detalheService.carregar(id);
        if (detalhe.isEmpty()) {
            redirectAttributes.addFlashAttribute("mensagemErro", "Serviço não encontrado ou indisponível.");
            return "redirect:/vitrine";
        }
        var d = detalhe.get();
        model.addAttribute("servico", d.servico());
        model.addAttribute("prestador", d.servico().getPrestador());
        model.addAttribute("fotos", d.fotos());
        model.addAttribute("avaliacoes", d.avaliacoes());
        model.addAttribute("mediaServico", d.mediaServico());
        model.addAttribute("totalAvaliacoesPrestador", d.totalAvaliacoesPrestador());
        model.addAttribute("mediaPrestador", d.mediaPrestador());
        model.addAttribute("podeGerenciar", podeGerenciarServico(id));
        return "servicos/detalhes";
    }

    @GetMapping("/servicos/novo")
    public String createForm(Model model, RedirectAttributes redirectAttributes) {
        boolean admin = acessoService.isAdmin();
        boolean prestador = acessoService.hasPapel(PapelUsuario.PRESTADOR)&&(perfilAtivo==null||perfilAtivo.atual()==PapelUsuario.PRESTADOR);
        if (!admin && !prestador) {
            redirectAttributes.addFlashAttribute("mensagemErro", "Voce nao tem permissao para criar servicos.");
            return "redirect:/servicos";
        }

        model.addAttribute("servico", new Servico());

        if (admin) {
            model.addAttribute("prestadores", prestadorService.findAllActive());
        } else {
            Usuario usuario = acessoService.getUsuarioAtualOrThrow();
            Prestador prestadorAtual = prestadorService.findByUsuarioId(usuario.getId()).orElse(null);
            if (prestadorAtual == null) {
                redirectAttributes.addFlashAttribute("mensagemErro", "Seu usuario nao possui cadastro de prestador.");
                return "redirect:/servicos";
            }
            model.addAttribute("prestadores", List.of(prestadorAtual));
            model.addAttribute("prestadorAtual", prestadorAtual);
        }

        model.addAttribute("categorias", categoriaService.findAll());
        model.addAttribute("isAdmin", admin);
        return "servicos/form";
    }

    @PostMapping("/servicos")
    public String save(@ModelAttribute Servico servico, org.springframework.validation.BindingResult binding,
                       @RequestParam(value = "fotos", required = false) List<org.springframework.web.multipart.MultipartFile> fotos,
                       RedirectAttributes redirectAttributes) {
        if (binding.hasFieldErrors("preco")) {
            redirectAttributes.addFlashAttribute("mensagemErro", com.obraslink.validation.Moeda.MENSAGEM_INVALIDO);
            return "redirect:/servicos/novo";
        }
        boolean admin = acessoService.isAdmin();
        boolean prestador = acessoService.hasPapel(PapelUsuario.PRESTADOR)&&(perfilAtivo==null||perfilAtivo.atual()==PapelUsuario.PRESTADOR);
        if (!admin && !prestador) {
            redirectAttributes.addFlashAttribute("mensagemErro", "Voce nao tem permissao para criar servicos.");
            return "redirect:/servicos";
        }

        if (!admin) {
            Usuario usuario = acessoService.getUsuarioAtualOrThrow();
            Prestador prestadorAtual = prestadorService.findByUsuarioId(usuario.getId()).orElse(null);
            if (prestadorAtual == null) {
                redirectAttributes.addFlashAttribute("mensagemErro", "Seu usuario nao possui cadastro de prestador.");
                return "redirect:/servicos";
            }
            servico.setPrestador(prestadorAtual);
        }
        String erroCategoria = aplicarCategoriaDoPrestador(servico, admin);
        if (erroCategoria != null) {
            redirectAttributes.addFlashAttribute("mensagemErro", erroCategoria);
            return "redirect:/servicos/novo";
        }

        Servico salvo = servicoService.save(servico);
        redirectAttributes.addFlashAttribute("mensagem", "Serviço criado com sucesso!");
        anexarFotos(salvo.getId(), fotos, redirectAttributes);
        return "redirect:/servicos";
    }

    @GetMapping("/servicos/{id}/editar")
    public String editForm(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        if (!podeGerenciarServico(id)) {
            redirectAttributes.addFlashAttribute("mensagemErro", "Voce nao tem permissao para editar este servico.");
            return "redirect:/servicos";
        }

        boolean admin = acessoService.isAdmin();

        return servicoService.findById(id).map(servico -> {
            model.addAttribute("servico", servico);
            if (admin) {
                model.addAttribute("prestadores", prestadorService.findAllActive());
            } else {
                model.addAttribute("prestadores", List.of(servico.getPrestador()));
                model.addAttribute("prestadorAtual", servico.getPrestador());
            }
            model.addAttribute("categorias", categoriaService.findAll());
            model.addAttribute("isAdmin", admin);
            model.addAttribute("fotosServico", imagemService == null ? List.of() : imagemService.fotosDoServico(id));
            model.addAttribute("maxFotos", com.obraslink.service.ImagemService.MAX_FOTOS_POR_SERVICO);
            return "servicos/form";
        }).orElse("redirect:/servicos");
    }

    @PostMapping("/servicos/{id}")
    public String update(@PathVariable Long id,
                         @ModelAttribute Servico servico, org.springframework.validation.BindingResult binding,
                         @RequestParam(value = "fotos", required = false) List<org.springframework.web.multipart.MultipartFile> fotos,
                         RedirectAttributes redirectAttributes) {
        if (binding.hasFieldErrors("preco")) {
            redirectAttributes.addFlashAttribute("mensagemErro", com.obraslink.validation.Moeda.MENSAGEM_INVALIDO);
            return "redirect:/servicos/" + id + "/editar";
        }
        if (!podeGerenciarServico(id)) {
            redirectAttributes.addFlashAttribute("mensagemErro", "Voce nao tem permissao para editar este servico.");
            return "redirect:/servicos";
        }

        Servico existente = servicoService.findById(id).orElse(null);
        if (existente == null) {
            redirectAttributes.addFlashAttribute("mensagemErro", "Servico nao encontrado.");
            return "redirect:/servicos";
        }

        servico.setId(id);

        boolean admin = acessoService.isAdmin();
        if (!admin) {
            Usuario usuario = acessoService.getUsuarioAtualOrThrow();
            Prestador prestadorAtual = prestadorService.findByUsuarioId(usuario.getId()).orElse(null);
            if (prestadorAtual == null) {
                redirectAttributes.addFlashAttribute("mensagemErro", "Seu usuario nao possui cadastro de prestador.");
                return "redirect:/servicos";
            }
            servico.setPrestador(prestadorAtual);
        } else if (servico.getPrestador() == null) {
            servico.setPrestador(existente.getPrestador());
        }
        String erroCategoria = aplicarCategoriaDoPrestador(servico, admin);
        if (erroCategoria != null) {
            redirectAttributes.addFlashAttribute("mensagemErro", erroCategoria);
            return "redirect:/servicos/" + id + "/editar";
        }

        servicoService.save(servico);
        redirectAttributes.addFlashAttribute("mensagem", "Serviço atualizado com sucesso!");
        anexarFotos(id, fotos, redirectAttributes);
        return "redirect:/servicos";
    }

    @PostMapping("/servicos/{id}/excluir")
    public String delete(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        if (!podeGerenciarServico(id)) {
            redirectAttributes.addFlashAttribute("mensagemErro", "Voce nao tem permissao para excluir este servico.");
            return "redirect:/servicos";
        }

        servicoService.delete(id);
        redirectAttributes.addFlashAttribute("mensagem", "Servico excluido com sucesso!");
        return "redirect:/servicos";
    }
}
