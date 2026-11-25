package com.obraslink.service;

import com.obraslink.dto.OrcamentoFormularioRequest;
import com.obraslink.model.*;
import com.obraslink.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Pedido de orçamento a vários prestadores da mesma categoria: o cliente preenche o formulário geral
 * (+ campos específicos da categoria, ver {@link CampoOrcamentoCatalogo}) uma única vez, o sistema gera
 * uma mensagem individual por prestador (mesmo modelo de comunicação do contato avulso, ver
 * {@link AtendimentoService}) que o cliente pode revisar e ajustar, e cada envio é feito separadamente —
 * nenhum prestador vê a mensagem ou a resposta dos demais.
 */
@Service
@RequiredArgsConstructor
public class OrcamentoService {
    private static final DateTimeFormatter DATA_BR = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final AcessoService acesso;
    private final PerfilAtivoService perfil;
    private final ClienteRepository clientes;
    private final PrestadorRepository prestadores;
    private final SolicitacaoOrcamentoRepository solicitacoes;
    private final SolicitacaoOrcamentoItemRepository itens;
    private final SolicitacaoOrcamentoAnexoRepository anexosRepo;
    private final OrcamentoAnexoService anexoService;
    private final AtendimentoService atendimentoService;

    public record PrestadorSelecionavel(Long id, String nome) {}

    /**
     * Cria o rascunho da solicitação a partir dos prestadores marcados na vitrine: todos precisam ter ao
     * menos uma especialidade em comum (a principal ou uma secundária de cada um). Se houver só uma em
     * comum, ela já é usada; se houver mais de uma, o cliente escolhe em seguida (ver {@link #escolherEspecialidades}).
     */
    @Transactional
    public Long iniciar(List<Long> prestadorIds) {
        Usuario usuario = exigirClienteAtual();
        List<Long> ids = prestadorIds == null ? List.of() : prestadorIds.stream().distinct().toList();
        if (ids.size() < 1) throw new IllegalArgumentException("Selecione pelo menos um prestador.");
        List<Prestador> selecionados = prestadores.findAllById(ids);
        if (selecionados.size() != ids.size()) throw new IllegalArgumentException("Um dos prestadores selecionados não foi encontrado.");
        for (Prestador prestador : selecionados) {
            if (!Boolean.TRUE.equals(prestador.getAtivo()) || prestador.getUsuarioId() == null) {
                throw new IllegalArgumentException(prestador.getNome() + " não está disponível para receber contatos.");
            }
            if (prestador.getUsuarioId().equals(usuario.getId())) {
                throw new IllegalArgumentException("Você não pode solicitar um orçamento para si mesmo.");
            }
            if (prestador.getEspecialidades().isEmpty()) {
                throw new IllegalArgumentException(prestador.getNome() + " não tem categoria definida.");
            }
        }
        List<Categoria> comuns = especialidadesComuns(selecionados);
        if (comuns.isEmpty()) {
            throw new IllegalArgumentException("Selecione prestadores que tenham ao menos uma especialidade em comum.");
        }

        SolicitacaoOrcamento solicitacao = new SolicitacaoOrcamento();
        solicitacao.setClienteUsuarioId(usuario.getId());
        if (comuns.size() == 1) solicitacao.setCategoriasNomes(new ArrayList<>(List.of(comuns.get(0).getNome())));
        solicitacoes.save(solicitacao);

        List<SolicitacaoOrcamentoItem> novosItens = selecionados.stream()
                .map(p -> SolicitacaoOrcamentoItem.builder()
                        .solicitacaoId(solicitacao.getId())
                        .prestadorId(p.getId())
                        .prestadorNome(p.getNome())
                        .mensagem("")
                        .build())
                .toList();
        itens.saveAll(novosItens);
        return solicitacao.getId();
    }

    /** Especialidades (principal + secundárias) comuns a todos os prestadores desta lista, na ordem do primeiro. */
    private List<Categoria> especialidadesComuns(List<Prestador> lista) {
        List<Categoria> comuns = new ArrayList<>(lista.get(0).getEspecialidades());
        for (int i = 1; i < lista.size(); i++) {
            Set<Long> idsDoOutro = lista.get(i).getEspecialidades().stream().map(Categoria::getId).collect(Collectors.toSet());
            comuns.removeIf(c -> !idsDoOutro.contains(c.getId()));
        }
        return comuns;
    }

    /**
     * Especialidades em comum entre os prestadores desta solicitação, recalculadas a partir dos dados
     * atuais deles (podem ter mudado depois da seleção). Vazia só se a solicitação não tiver itens.
     */
    @Transactional(readOnly = true)
    public List<Categoria> especialidadesComunsDaSolicitacao(Long id) {
        solicitacaoDoClienteAtual(id);
        List<Long> prestadorIds = itens.findBySolicitacaoIdOrderByIdAsc(id).stream().map(SolicitacaoOrcamentoItem::getPrestadorId).toList();
        List<Prestador> provs = prestadores.findAllById(prestadorIds);
        return provs.isEmpty() ? List.of() : especialidadesComuns(provs);
    }

    /** Registra para quais das especialidades em comum o cliente quer pedir o orçamento (uma ou mais). */
    @Transactional
    public void escolherEspecialidades(Long id, List<Long> categoriaIds) {
        SolicitacaoOrcamento solicitacao = solicitacaoDoClienteAtual(id);
        if (categoriaIds == null || categoriaIds.isEmpty()) {
            throw new IllegalArgumentException("Selecione ao menos uma especialidade.");
        }
        List<Long> escolhidos = categoriaIds.stream().distinct().toList();
        List<Categoria> comuns = especialidadesComunsDaSolicitacao(id);
        List<String> nomes = comuns.stream().filter(c -> escolhidos.contains(c.getId())).map(Categoria::getNome).toList();
        if (nomes.size() != escolhidos.size()) {
            throw new IllegalArgumentException("Uma das especialidades escolhidas não é mais comum a todos os prestadores selecionados.");
        }
        solicitacao.setCategoriasNomes(new ArrayList<>(nomes));
    }

    @Transactional(readOnly = true)
    public SolicitacaoOrcamento consultarFormulario(Long id) {
        return solicitacaoDoClienteAtual(id);
    }

    @Transactional(readOnly = true)
    public List<SolicitacaoOrcamentoItem> itensDaSolicitacao(Long id) {
        solicitacaoDoClienteAtual(id);
        return itens.findBySolicitacaoIdOrderByIdAsc(id);
    }

    @Transactional(readOnly = true)
    public List<SolicitacaoOrcamentoAnexo> anexosDaSolicitacao(Long id) {
        solicitacaoDoClienteAtual(id);
        return anexosRepo.findBySolicitacaoIdOrderByIdAsc(id);
    }

    /** Endereço do perfil do cliente atual, sugerido como padrão no formulário; null se ele ainda não tiver um. */
    @Transactional(readOnly = true)
    public Endereco enderecoCadastradoAtual() {
        Usuario usuario = exigirClienteAtual();
        return clientes.findByUsuarioId(usuario.getId()).map(Cliente::getEndereco).orElse(null);
    }

    /** Grava os campos preenchidos, anexa as imagens de referência enviadas e gera a mensagem de cada prestador. */
    @Transactional
    public void salvarFormulario(Long id, OrcamentoFormularioRequest form, List<MultipartFile> novosAnexos) {
        SolicitacaoOrcamento solicitacao = solicitacaoDoClienteAtual(id);
        if (solicitacao.getCategoriasNomes().isEmpty()) {
            throw new IllegalArgumentException("Escolha para quais especialidades você quer pedir o orçamento.");
        }
        Endereco endereco = resolverEnderecoExecucao(solicitacao, form);
        if (form.getDescricaoGeral() == null || form.getDescricaoGeral().isBlank()) {
            throw new IllegalArgumentException("Descreva o serviço que você precisa.");
        }
        Map<String, String> valores = new LinkedHashMap<>();
        List<String> categoriasNomes = solicitacao.getCategoriasNomes();
        for (int indice = 0; indice < categoriasNomes.size(); indice++) {
            for (CampoOrcamentoCatalogo.Campo campo : CampoOrcamentoCatalogo.campos(categoriasNomes.get(indice))) {
                String chaveComposta = chaveComposta(indice, campo);
                String valor = form.getCampos() == null ? null : form.getCampos().get(chaveComposta);
                if (campo.obrigatorio() && (valor == null || valor.isBlank())) {
                    throw new IllegalArgumentException("Preencha o campo \"" + campo.rotulo() + "\".");
                }
                if (valor != null && !valor.isBlank()) valores.put(chaveComposta, valor.trim());
            }
        }

        List<MultipartFile> validos = novosAnexos == null ? List.of()
                : novosAnexos.stream().filter(a -> a != null && !a.isEmpty()).toList();
        long existentes = anexosRepo.countBySolicitacaoId(id);
        if (existentes + validos.size() > OrcamentoAnexoService.MAX_ANEXOS_POR_SOLICITACAO) {
            throw new IllegalArgumentException("No máximo " + OrcamentoAnexoService.MAX_ANEXOS_POR_SOLICITACAO + " anexos por solicitação.");
        }
        List<SolicitacaoOrcamentoAnexo> novos = new ArrayList<>();
        for (MultipartFile arquivo : validos) {
            String chave = anexoService.armazenar(arquivo);
            novos.add(SolicitacaoOrcamentoAnexo.builder().solicitacaoId(id).chave(chave).build());
        }
        anexosRepo.saveAll(novos);

        solicitacao.setEnderecoExecucao(endereco);
        solicitacao.setDescricaoGeral(form.getDescricaoGeral().trim());
        solicitacao.setDataPrevisao(form.getDataPrevisao());
        solicitacao.setUrgencia(form.getUrgencia() == null ? SolicitacaoOrcamento.Urgencia.MEDIA : form.getUrgencia());
        solicitacao.setInformacoesComplementares(form.getInformacoesComplementares() == null ? null : form.getInformacoesComplementares().trim());
        solicitacao.setCamposEspecificos(valores);

        int totalAnexos = (int) (existentes + novos.size());
        Cliente cliente = clientes.findByUsuarioId(solicitacao.getClienteUsuarioId())
                .orElseThrow(() -> new IllegalArgumentException("Complete seu cadastro de cliente antes de continuar."));
        for (SolicitacaoOrcamentoItem item : itens.findBySolicitacaoIdOrderByIdAsc(id)) {
            item.setMensagem(gerarMensagem(cliente, solicitacao, item.getPrestadorNome(), totalAnexos));
        }
        itens.saveAll(itens.findBySolicitacaoIdOrderByIdAsc(id));
    }

    /**
     * O endereço do pedido é sempre próprio da solicitação (nunca a mesma linha do cadastro do cliente),
     * para que alterá-lo aqui nunca mude o endereço do perfil e cada solicitação possa ter o seu.
     */
    private Endereco resolverEnderecoExecucao(SolicitacaoOrcamento solicitacao, OrcamentoFormularioRequest form) {
        Endereco escolhido = new Endereco();
        if (form.isUsarEnderecoCadastrado()) {
            Endereco cadastro = enderecoCadastradoAtual();
            if (cadastro == null) {
                throw new IllegalArgumentException("Você ainda não tem um endereço cadastrado no perfil. Informe o endereço de execução.");
            }
            escolhido.atualizarCom(cadastro);
        } else {
            Endereco informado = form.getEnderecoExecucao();
            if (informado == null || vazio(informado.getLogradouro()) || vazio(informado.getNumero()) || vazio(informado.getBairro())
                    || vazio(informado.getCidade()) || vazio(informado.getEstado()) || vazio(informado.getCep())) {
                throw new IllegalArgumentException("Informe o endereço de execução completo (CEP, rua, número, bairro, cidade e estado).");
            }
            informado.setLatitude(null);
            informado.setLongitude(null);
            informado.setEstado(informado.getEstado().trim().toUpperCase(Locale.ROOT));
            escolhido.atualizarCom(informado);
        }
        Endereco atual = solicitacao.getEnderecoExecucao();
        if (atual == null) return escolhido;
        atual.atualizarCom(escolhido);
        return atual;
    }

    private static boolean vazio(String valor) {
        return valor == null || valor.isBlank();
    }

    /**
     * Chave usada para gravar e ler um campo específico: namespaceada pela posição da categoria em
     * {@code categoriasNomes} (estável, já que a ordem é gravada e nunca muda depois de escolhida),
     * para não colidir quando duas especialidades escolhidas tiverem um campo de mesma chave (ex.: "areaM2").
     */
    private static String chaveComposta(int indiceCategoria, CampoOrcamentoCatalogo.Campo campo) {
        return "c" + indiceCategoria + "_" + campo.chave();
    }

    @Transactional
    public void excluirAnexo(Long solicitacaoId, Long anexoId) {
        solicitacaoDoClienteAtual(solicitacaoId);
        SolicitacaoOrcamentoAnexo anexo = anexosRepo.findByIdAndSolicitacaoId(anexoId, solicitacaoId)
                .orElseThrow(() -> new IllegalArgumentException("Anexo não encontrado."));
        anexosRepo.delete(anexo);
        anexoService.excluirArquivo(anexo.getChave());
    }

    public record EnvioResultado(String prestadorNome, String linkWhatsapp) {}

    /**
     * Grava os ajustes feitos na revisão (uma mensagem por item, o cliente pode editar antes de enviar) e
     * envia cada uma, individualmente, reaproveitando a trava/deduplicação do contato avulso.
     */
    @Transactional
    public List<EnvioResultado> confirmarEnvio(Long solicitacaoId, Map<Long, String> mensagensEditadas) {
        SolicitacaoOrcamento solicitacao = solicitacaoDoClienteAtual(solicitacaoId);
        List<SolicitacaoOrcamentoItem> lista = itens.findBySolicitacaoIdOrderByIdAsc(solicitacaoId);
        if (lista.isEmpty()) throw new IllegalArgumentException("Nenhum prestador selecionado nesta solicitação.");
        if (mensagensEditadas != null) {
            for (SolicitacaoOrcamentoItem item : lista) {
                String editada = mensagensEditadas.get(item.getId());
                if (editada != null && !editada.isBlank()) item.setMensagem(editada.trim());
            }
        }
        if (lista.stream().anyMatch(i -> i.getMensagem() == null || i.getMensagem().isBlank())) {
            throw new IllegalArgumentException("Preencha o formulário antes de enviar.");
        }
        List<EnvioResultado> resultados = new ArrayList<>();
        for (SolicitacaoOrcamentoItem item : lista) {
            String link = atendimentoService.contatarComMensagem(item.getPrestadorId(), item.getMensagem());
            item.setEnviadoEm(java.time.LocalDateTime.now());
            resultados.add(new EnvioResultado(item.getPrestadorNome(), link));
        }
        itens.saveAll(lista);
        solicitacao.setStatus(SolicitacaoOrcamento.Status.ENVIADA);
        solicitacoes.save(solicitacao);
        return resultados;
    }

    private String gerarMensagem(Cliente cliente, SolicitacaoOrcamento solicitacao, String prestadorNome, int totalAnexos) {
        StringBuilder msg = new StringBuilder();
        msg.append("Olá, sou ").append(cliente.getNome()).append(". Vim pelo ObraLink e gostaria de solicitar um orçamento de ")
                .append(solicitacao.getCategoriasResumo()).append(" com ").append(prestadorNome).append(".");
        msg.append("\nLocal: ").append(solicitacao.getEnderecoExecucao().getEnderecoCompleto());
        msg.append("\nDescrição: ").append(solicitacao.getDescricaoGeral());
        msg.append("\nPrevisão: ").append(solicitacao.getDataPrevisao() == null ? "A combinar" : DATA_BR.format(solicitacao.getDataPrevisao()));
        msg.append("\nUrgência: ").append(urgenciaTexto(solicitacao.getUrgencia()));
        List<String> categoriasNomes = solicitacao.getCategoriasNomes();
        for (int indice = 0; indice < categoriasNomes.size(); indice++) {
            for (CampoOrcamentoCatalogo.Campo campo : CampoOrcamentoCatalogo.campos(categoriasNomes.get(indice))) {
                String valor = solicitacao.getCamposEspecificos().get(chaveComposta(indice, campo));
                if (valor != null && !valor.isBlank()) msg.append("\n").append(campo.rotulo()).append(": ").append(valor);
            }
        }
        if (totalAnexos > 0) {
            msg.append("\nAnexei ").append(totalAnexos).append(totalAnexos == 1 ? " arquivo" : " arquivos")
                    .append(" de referência no ObraLink — acesse a solicitação pelo site para ver.");
        }
        if (solicitacao.getInformacoesComplementares() != null && !solicitacao.getInformacoesComplementares().isBlank()) {
            msg.append("\nInformações complementares: ").append(solicitacao.getInformacoesComplementares());
        }
        msg.append("\nPoderia me passar mais detalhes?");
        return msg.toString();
    }

    private static String urgenciaTexto(SolicitacaoOrcamento.Urgencia urgencia) {
        return switch (urgencia) {
            case BAIXA -> "Sem pressa";
            case MEDIA -> "Moderada";
            case ALTA -> "Urgente";
        };
    }

    private Usuario exigirClienteAtual() {
        Usuario usuario = acesso.getUsuarioAtualOrThrow();
        if (!Boolean.TRUE.equals(usuario.getAtivo()) || !usuario.getPapeis().contains(PapelUsuario.CLIENTE) || perfil.atual() != PapelUsuario.CLIENTE) {
            throw new IllegalArgumentException("Entre com um perfil de cliente para solicitar um orçamento.");
        }
        return usuario;
    }

    private SolicitacaoOrcamento solicitacaoDoClienteAtual(Long id) {
        Usuario usuario = exigirClienteAtual();
        return solicitacoes.findByIdAndClienteUsuarioId(id, usuario.getId())
                .orElseThrow(() -> new IllegalArgumentException("Solicitação não encontrada."));
    }
}
