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
    @org.springframework.beans.factory.annotation.Autowired(required=false)
    private com.obraslink.service.ImagemService imagemService;

    private boolean podeGerenciarPrestador(Long id){return acessoService.isAdmin()||(perfilAtivoService==null||perfilAtivoService.atual()==PapelUsuario.PRESTADOR)&&acessoService.podeGerenciarPrestador(id);}

    @org.springframework.beans.factory.annotation.Autowired(required=false)
    private com.obraslink.service.ClienteService clienteService;
    @org.springframework.beans.factory.annotation.Autowired(required=false)
    private com.obraslink.service.LocalizacaoService localizacao;

    /** Ponto do mapa (coordenada aproximada, ~1 km, para não expor o endereço exato). */
    public record PontoMapa(Long id, String nome, String categoria, double lat, double lng, int raioKm, Double distanciaKm, boolean atende) {}

    /** Endereço do cliente logado, quando ele está usando o perfil de cliente. */
    private com.obraslink.model.Endereco enderecoDoClienteAtual(Optional<Usuario> usuario) {
        if (clienteService == null || usuario.isEmpty() || !usuario.get().getPapeis().contains(PapelUsuario.CLIENTE)) return null;
        if (perfilAtivoService != null && perfilAtivoService.atual() != PapelUsuario.CLIENTE) return null;
        return clienteService.findByUsuarioId(usuario.get().getId()).map(com.obraslink.model.Cliente::getEndereco).orElse(null);
    }

    /** Endereço obrigatório e raio válido antes de salvar (busca de coordenadas fora de transação). */
    private void prepararLocalizacao(Prestador prestador) {
        if (prestador.getEndereco() == null) throw new IllegalArgumentException("Informe o endereço de atendimento.");
        if (localizacao != null) localizacao.completarCoordenadas(prestador.getEndereco());
        com.obraslink.service.LocalizacaoService.validarEndereco(prestador.getEndereco());
        prestador.setRaioAtendimentoKm(com.obraslink.service.LocalizacaoService.validarRaio(prestador.getRaioAtendimentoKm()));
    }

    @GetMapping
    public String list(@RequestParam(required = false) Long categoriaId, @RequestParam(required = false) String regiao, Model model) {
        Optional<Usuario> usuarioOpt = acessoService.getUsuarioAtual();
        boolean admin = usuarioOpt.map(acessoService::isAdmin).orElse(false);
        Long usuarioAtualId = usuarioOpt.map(Usuario::getId).orElse(null);

        List<Prestador> prestadores = new java.util.ArrayList<>(categoriaId == null
                ? prestadorService.findAllActive() : prestadorService.findByCategoria(categoriaId));
        // Proximidade: distância até o endereço do cliente e se o prestador atende a região (raio dele)
        var origem = enderecoDoClienteAtual(usuarioOpt);
        boolean temLocalizacao = origem != null && origem.temCoordenadas();
        java.util.Map<Long, Double> distancias = new java.util.HashMap<>();
        java.util.Set<Long> atendeRegiao = new java.util.HashSet<>();
        if (temLocalizacao) {
            for (Prestador p : prestadores) {
                com.obraslink.service.LocalizacaoService.distanciaKm(origem, p.getEndereco()).ifPresent(d -> {
                    distancias.put(p.getId(), d);
                    if (d <= p.getRaioEfetivoKm()) atendeRegiao.add(p.getId());
                });
            }
            prestadores.sort(java.util.Comparator.comparing(p -> distancias.getOrDefault(p.getId(), Double.MAX_VALUE)));
        }
        boolean soRegiao = temLocalizacao && "1".equals(regiao);
        if (soRegiao) prestadores.removeIf(p -> !atendeRegiao.contains(p.getId()));

        List<PontoMapa> pontos = prestadores.stream()
                .filter(p -> p.getEndereco() != null && p.getEndereco().temCoordenadas())
                .map(p -> new PontoMapa(p.getId(), p.getNome(), p.getCategoria() == null ? "" : p.getCategoria().getNome(),
                        com.obraslink.service.LocalizacaoService.aproximar(p.getEndereco().getLatitude()),
                        com.obraslink.service.LocalizacaoService.aproximar(p.getEndereco().getLongitude()),
                        p.getRaioEfetivoKm(), distancias.get(p.getId()), atendeRegiao.contains(p.getId())))
                .toList();

        model.addAttribute("prestadores", prestadores);
        model.addAttribute("distancias", distancias);
        model.addAttribute("atendeRegiao", atendeRegiao);
        model.addAttribute("temLocalizacao", temLocalizacao);
        model.addAttribute("soRegiao", soRegiao);
        model.addAttribute("pontosMapa", pontos);
        // Dados do mapa da lista (serializados com escape pelo th:inline="javascript")
        java.util.Map<String, Object> mapaDados = new java.util.HashMap<>();
        mapaDados.put("pontos", pontos);
        mapaDados.put("origem", temLocalizacao ? new double[]{origem.getLatitude(), origem.getLongitude()} : null);
        model.addAttribute("mapaDados", mapaDados);
        model.addAttribute("categorias", categoriaService.findAll());
        model.addAttribute("categoriaAtiva", categoriaId);
        model.addAttribute("isAdmin", admin);
        model.addAttribute("usuarioAtualId", usuarioAtualId);
        // IDs favoritados pelo cliente atual (uma consulta só), para o coração de cada card.
        model.addAttribute("favoritosPrestadores", relacionamentosService == null ? java.util.Set.of() : relacionamentosService.idsFavoritosDoAtual());
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

        prestador.setFotoPerfilChave(null); // fotos só pelas rotas de imagem
        prestador.setBannerChave(null);
        prepararLocalizacao(prestador);
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
            List<com.obraslink.model.Servico> servicosDoPerfil = servicoService==null?List.of():servicoService.findByPrestador(prestador.getId());
            model.addAttribute("servicos", servicosDoPerfil);
            // Fotos de todos os serviços do perfil numa consulta só
            model.addAttribute("fotosServicos", imagemService == null || servicosDoPerfil.isEmpty() ? java.util.Map.of()
                    : imagemService.fotosPorServico(servicosDoPerfil.stream().map(com.obraslink.model.Servico::getId).toList()));
            model.addAttribute("disponibilidades",relacionamentosService==null?List.of():relacionamentosService.disponibilidade(prestador.getId()));
            Long clienteId=acessoService.getUsuarioAtual().flatMap(u->u.getPapeis().contains(PapelUsuario.CLIENTE)?java.util.Optional.ofNullable(u.getId()):java.util.Optional.<Long>empty()).orElse(null);
            model.addAttribute("favorito",clienteId!=null&&relacionamentosService!=null&&relacionamentosService.ehFavorito(clienteId,prestador.getId()));
            model.addAttribute("perfilAtivo",perfilAtivoService==null||perfilAtivoService.atual()==null?"ADMIN":perfilAtivoService.atual().name());
            // Mapa da área de atendimento (centro aproximado + raio) e distância até o cliente logado
            var endereco = prestador.getEndereco();
            if (endereco != null && endereco.temCoordenadas()) {
                model.addAttribute("mapaLat", com.obraslink.service.LocalizacaoService.aproximar(endereco.getLatitude()));
                model.addAttribute("mapaLng", com.obraslink.service.LocalizacaoService.aproximar(endereco.getLongitude()));
            }
            var origem = enderecoDoClienteAtual(acessoService.getUsuarioAtual());
            com.obraslink.service.LocalizacaoService.distanciaKm(origem, endereco).ifPresent(d -> {
                model.addAttribute("distanciaKm", d);
                model.addAttribute("atendeVoce", d <= prestador.getRaioEfetivoKm());
                model.addAttribute("origemLat", origem.getLatitude());
                model.addAttribute("origemLng", origem.getLongitude());
            });
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
        prestador.setRaioAtendimentoKm(dados.getRaioAtendimentoKm());
        if (dados.getEndereco() != null) {
            com.obraslink.model.Endereco atual = prestador.getEndereco() == null ? new com.obraslink.model.Endereco() : prestador.getEndereco();
            atual.atualizarCom(dados.getEndereco());
            prestador.setEndereco(atual);
        }
        prepararLocalizacao(prestador);
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
        // Fotos só mudam pelas rotas de imagem; o formulário não pode trocar a chave do arquivo.
        prestador.setFotoPerfilChave(existente.getFotoPerfilChave());
        prestador.setBannerChave(existente.getBannerChave());
        prestador.setCategorias(existente.getCategorias() == null
                ? new java.util.ArrayList<>()
                : new java.util.ArrayList<>(existente.getCategorias()));
        if (!acessoService.isAdmin()) {
            prestador.setEmail(existente.getEmail());
        }
        // Atualiza o mesmo registro de endereço em vez de criar outro
        if (prestador.getEndereco() == null) prestador.setEndereco(existente.getEndereco());
        else if (existente.getEndereco() != null) prestador.getEndereco().setId(existente.getEndereco().getId());
        prepararLocalizacao(prestador);

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
