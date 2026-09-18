package com.obraslink.service;

import com.obraslink.model.*;
import com.obraslink.repository.*;
import com.obraslink.validation.DadosCadastrais;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AtendimentoService {
    private final AcessoService acessoService;
    private final ClienteRepository clientes;
    private final AtendimentoRepository atendimentos;
    private final NotificacaoService notificacoes;
    private final EntityManager entityManager;

    @Transactional
    public String contatar(Long prestadorId) {
        Usuario usuario = acessoService.getUsuarioAtualOrThrow();
        if (!Boolean.TRUE.equals(usuario.getAtivo()) || !usuario.getPapeis().contains(PapelUsuario.CLIENTE)) {
            throw new IllegalArgumentException("Entre com um perfil de cliente para solicitar um serviço.");
        }
        // Serializa cliques simultâneos no mesmo fornecedor antes de procurar contato aberto.
        Prestador prestador = entityManager.find(Prestador.class, prestadorId, LockModeType.PESSIMISTIC_WRITE);
        if (prestador == null || !Boolean.TRUE.equals(prestador.getAtivo()) || prestador.getUsuarioId() == null) {
            throw new IllegalArgumentException("Este fornecedor não está disponível para receber contatos.");
        }
        Usuario fornecedor = entityManager.find(Usuario.class, prestador.getUsuarioId());
        if (fornecedor == null || !Boolean.TRUE.equals(fornecedor.getAtivo())) {
            throw new IllegalArgumentException("A conta deste fornecedor não está disponível.");
        }
        if (usuario.getId().equals(prestador.getUsuarioId())) {
            throw new IllegalArgumentException("Você não pode solicitar um serviço para si mesmo.");
        }
        if (!DadosCadastrais.telefoneValido(prestador.getWhatsapp())) {
            throw new IllegalArgumentException("O fornecedor precisa cadastrar um WhatsApp válido com DDD.");
        }
        String numero = "55" + DadosCadastrais.telefoneSemMascara(prestador.getWhatsapp());
        Cliente cliente = clientes.findByUsuarioId(usuario.getId())
                .orElseThrow(() -> new IllegalArgumentException("Complete seu cadastro de cliente antes de entrar em contato."));
        Atendimento atendimento = atendimentos.findFirstByClienteUsuarioIdAndPrestadorIdAndStatusNotOrderByIdDesc(
                usuario.getId(), prestadorId, Atendimento.Status.CONCLUIDO).orElse(null);
        if (atendimento == null) {
            atendimento = new Atendimento();
            atendimento.setClienteUsuarioId(usuario.getId());
            atendimento.setPrestadorUsuarioId(prestador.getUsuarioId());
            atendimento.setPrestadorId(prestadorId);
            atendimento.setClienteNome(cliente.getNome());
            atendimento.setPrestadorNome(prestador.getNome());
            atendimentos.save(atendimento);
            notificacoes.criar(prestador.getUsuarioId(), cliente.getNome() + " solicitou contato pelo WhatsApp. Confirme aqui quando fechar o serviço.", "/notificacoes");
        }
        String mensagem = "Olá, sou " + cliente.getNome() + ". Vim pelo Obras Link e gostaria de solicitar um orçamento com "
                + prestador.getNome() + ". Atendimento #" + atendimento.getId()
                + "\nCategoria: " + (prestador.getCategoria() == null ? "Não informada" : prestador.getCategoria().getNome())
                + "\nPreço mínimo: " + (prestador.getPrecoMinimo() == null ? "Sob consulta" : "R$ " + prestador.getPrecoMinimo())
                + "\nLocal: " + (prestador.getEndereco() == null ? "Não informado" : prestador.getEndereco().getCidade() + " - " + prestador.getEndereco().getEstado())
                + "\nPoderia me passar mais detalhes?";
        return "https://wa.me/" + numero + "?text=" + URLEncoder.encode(mensagem, StandardCharsets.UTF_8);
    }

    @Transactional(readOnly = true)
    public List<Atendimento> listar() {
        Long id = acessoService.getUsuarioAtualOrThrow().getId();
        return atendimentos.findByClienteUsuarioIdOrPrestadorUsuarioIdOrderByIdDesc(id, id);
    }

    @Transactional
    public void atualizar(Long id, boolean concluir) {
        Usuario usuario = acessoService.getUsuarioAtualOrThrow();
        Atendimento atendimento = entityManager.find(Atendimento.class, id, LockModeType.PESSIMISTIC_WRITE);
        if (atendimento == null || !Boolean.TRUE.equals(usuario.getAtivo())
                || !usuario.getId().equals(atendimento.getPrestadorUsuarioId())) {
            throw new IllegalArgumentException("Você não pode alterar este atendimento.");
        }
        Atendimento.Status destino = concluir ? Atendimento.Status.CONCLUIDO : Atendimento.Status.EM_ANDAMENTO;
        if (atendimento.getStatus() == destino) return;
        Atendimento.Status origem = concluir ? Atendimento.Status.EM_ANDAMENTO : Atendimento.Status.CONTATO_SOLICITADO;
        if (atendimento.getStatus() != origem) {
            throw new IllegalArgumentException("Esta alteração não é permitida na etapa atual do serviço.");
        }
        atendimento.setStatus(destino);
        if (concluir) atendimento.setConcluidoEm(LocalDateTime.now());
        else atendimento.setConfirmadoEm(LocalDateTime.now());
        notificacoes.criar(atendimento.getClienteUsuarioId(), atendimento.getPrestadorNome()
                + (concluir ? " marcou seu serviço como concluído." : " confirmou a contratação. Seu serviço está em andamento."), "/notificacoes");
    }
}
