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

    /** Cria o rascunho da solicitação a partir dos prestadores marcados na vitrine: todos precisam ser da mesma categoria. */
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
            if (prestador.getCategoria() == null) {
                throw new IllegalArgumentException(prestador.getNome() + " não tem categoria definida.");
            }
        }
        Categoria categoria = selecionados.get(0).getCategoria();
        boolean mesmaCategoria = selecionados.stream().allMatch(p -> categoria.getId().equals(p.getCategoria().getId()));
        if (!mesmaCategoria) {
            throw new IllegalArgumentException("Selecione prestadores da mesma categoria para pedir um orçamento em conjunto.");
        }

        SolicitacaoOrcamento solicitacao = new SolicitacaoOrcamento();
        solicitacao.setClienteUsuarioId(usuario.getId());
        solicitacao.setCategoriaId(categoria.getId());
        solicitacao.setCategoriaNome(categoria.getNome());
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
        Endereco endereco = resolverEnderecoExecucao(solicitacao, form);
        if (form.getDescricaoGeral() == null || form.getDescricaoGeral().isBlank()) {
            throw new IllegalArgumentException("Descreva o serviço que você precisa.");
        }
        List<CampoOrcamentoCatalogo.Campo> camposCategoria = CampoOrcamentoCatalogo.campos(solicitacao.getCategoriaNome());
        Map<String, String> valores = new LinkedHashMap<>();
        for (CampoOrcamentoCatalogo.Campo campo : camposCategoria) {
            String valor = form.getCampos() == null ? null : form.getCampos().get(campo.chave());
            if (campo.obrigatorio() && (valor == null || valor.isBlank())) {
                throw new IllegalArgumentException("Preencha o campo \"" + campo.rotulo() + "\".");
            }
            if (valor != null && !valor.isBlank()) valores.put(campo.chave(), valor.trim());
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
            item.setMensagem(gerarMensagem(cliente, solicitacao, item.getPrestadorNome(), camposCategoria, totalAnexos));
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

    private String gerarMensagem(Cliente cliente, SolicitacaoOrcamento solicitacao, String prestadorNome,
                                  List<CampoOrcamentoCatalogo.Campo> camposCategoria, int totalAnexos) {
        StringBuilder msg = new StringBuilder();
        msg.append("Olá, sou ").append(cliente.getNome()).append(". Vim pelo ObraLink e gostaria de solicitar um orçamento de ")
                .append(solicitacao.getCategoriaNome()).append(" com ").append(prestadorNome).append(".");
        msg.append("\nLocal: ").append(solicitacao.getEnderecoExecucao().getEnderecoCompleto());
        msg.append("\nDescrição: ").append(solicitacao.getDescricaoGeral());
        msg.append("\nPrevisão: ").append(solicitacao.getDataPrevisao() == null ? "A combinar" : DATA_BR.format(solicitacao.getDataPrevisao()));
        msg.append("\nUrgência: ").append(urgenciaTexto(solicitacao.getUrgencia()));
        for (CampoOrcamentoCatalogo.Campo campo : camposCategoria) {
            String valor = solicitacao.getCamposEspecificos().get(campo.chave());
            if (valor != null && !valor.isBlank()) msg.append("\n").append(campo.rotulo()).append(": ").append(valor);
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
