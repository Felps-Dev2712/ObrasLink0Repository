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
public class AtendimentoService {
    private final AcessoService acessoService;
    private final ClienteRepository clientes;
    private final AtendimentoRepository atendimentos;
    private final NotificacaoService notificacoes;
    private final EntityManager entityManager;
    private final ServicoRepository servicos;
    private final PerfilAtivoService perfil;

    public AtendimentoService(AcessoService acessoService, ClienteRepository clientes, AtendimentoRepository atendimentos,
            NotificacaoService notificacoes, EntityManager entityManager, ServicoRepository servicos) {
        this(acessoService,clientes,atendimentos,notificacoes,entityManager,servicos,null);
    }
    @org.springframework.beans.factory.annotation.Autowired
    public AtendimentoService(AcessoService acessoService, ClienteRepository clientes, AtendimentoRepository atendimentos,
            NotificacaoService notificacoes, EntityManager entityManager, ServicoRepository servicos, PerfilAtivoService perfil) {
        this.acessoService=acessoService;this.clientes=clientes;this.atendimentos=atendimentos;this.notificacoes=notificacoes;this.entityManager=entityManager;this.servicos=servicos;this.perfil=perfil;
    }
    public AtendimentoService(AcessoService acessoService, ClienteRepository clientes, AtendimentoRepository atendimentos,
            NotificacaoService notificacoes, EntityManager entityManager) {
        this(acessoService, clientes, atendimentos, notificacoes, entityManager, null);
    }

    @Transactional
    public String contatar(Long prestadorId) {
        return contatar(prestadorId,null);
    }

    @Transactional
    public String contatar(Long prestadorId, Long servicoId) {
        Usuario usuario = acessoService.getUsuarioAtualOrThrow();
        if (!Boolean.TRUE.equals(usuario.getAtivo()) || !usuario.getPapeis().contains(PapelUsuario.CLIENTE)||(perfil!=null&&perfil.atual()!=PapelUsuario.CLIENTE)) {
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
        Servico servicoSolicitado=null;
        if(servicoId!=null){
            if(servicos==null) throw new IllegalArgumentException("Não foi possível validar a disponibilidade do serviço.");
            servicoSolicitado=servicos.findById(servicoId).orElseThrow(()->new IllegalArgumentException("Serviço não encontrado."));
            if(!servicoSolicitado.getPrestador().getId().equals(prestadorId)||!Boolean.TRUE.equals(servicoSolicitado.getDisponivel())) throw new IllegalArgumentException("Este serviço está indisponível para contratação.");
        }
        String numero = "55" + DadosCadastrais.telefoneSemMascara(prestador.getWhatsapp());
        Cliente cliente = clientes.findByUsuarioId(usuario.getId())
                .orElseThrow(() -> new IllegalArgumentException("Complete seu cadastro de cliente antes de entrar em contato."));
        Atendimento atendimento = servicoId==null
                ? atendimentos.findFirstByClienteUsuarioIdAndPrestadorIdAndStatusNotOrderByIdDesc(usuario.getId(),prestadorId,Atendimento.Status.CONCLUIDO).orElse(null)
                : atendimentos.findFirstByClienteUsuarioIdAndPrestadorIdAndServicoIdAndStatusNotOrderByIdDesc(usuario.getId(),prestadorId,servicoId,Atendimento.Status.CONCLUIDO).orElse(null);
        if (atendimento == null) {
            atendimento = new Atendimento();
            atendimento.setClienteUsuarioId(usuario.getId());
            atendimento.setPrestadorUsuarioId(prestador.getUsuarioId());
            atendimento.setPrestadorId(prestadorId);
            atendimento.setServicoId(servicoId);
            atendimento.setServicoTitulo(servicoSolicitado==null?null:servicoSolicitado.getTitulo());
            atendimento.setClienteNome(cliente.getNome());
            atendimento.setPrestadorNome(prestador.getNome());
            atendimentos.save(atendimento);
            notificacoes.criar(prestador.getUsuarioId(), cliente.getNome() + " solicitou contato pelo WhatsApp. Confirme aqui quando fechar o serviço.", "/notificacoes");
        }
        String mensagem = "Olá, sou " + cliente.getNome() + ". Vim pelo ObraLink e gostaria de solicitar um orçamento"
                + (servicoSolicitado==null?"":" para " + servicoSolicitado.getTitulo()) + " com "
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
        atualizar(id, concluir, null, null);
    }

    @Transactional
    public void atualizar(Long id, boolean concluir, java.time.LocalDate data, java.time.LocalTime horario) {
        Usuario usuario = acessoService.getUsuarioAtualOrThrow();
        Atendimento atendimento = entityManager.find(Atendimento.class, id, LockModeType.PESSIMISTIC_WRITE);
        if (atendimento == null || !Boolean.TRUE.equals(usuario.getAtivo())
                || !usuario.getId().equals(atendimento.getPrestadorUsuarioId())||(perfil!=null&&perfil.atual()!=PapelUsuario.PRESTADOR)) {
            throw new IllegalArgumentException("Você não pode alterar este atendimento.");
        }
        Atendimento.Status destino = concluir ? Atendimento.Status.CONCLUIDO : Atendimento.Status.EM_ANDAMENTO;
        if (atendimento.getStatus() == destino) return;
        Atendimento.Status origem = concluir ? Atendimento.Status.EM_ANDAMENTO : Atendimento.Status.CONTATO_SOLICITADO;
        if (atendimento.getStatus() != origem) {
            throw new IllegalArgumentException("Esta alteração não é permitida na etapa atual do serviço.");
        }
        if (!concluir) {
            var hoje = java.time.LocalDate.now(java.time.ZoneId.of("America/Sao_Paulo"));
            if (data == null || horario == null) throw new IllegalArgumentException("Informe a data e o horário combinados.");
            if (data.isBefore(hoje)) throw new IllegalArgumentException("A data do serviço não pode ser anterior a hoje.");
            atendimento.setDataAgendada(data);
            atendimento.setHorarioAgendado(horario);
        }
        atendimento.setStatus(destino);
        if (concluir) atendimento.setConcluidoEm(LocalDateTime.now());
        else atendimento.setConfirmadoEm(LocalDateTime.now());
        notificacoes.criar(atendimento.getClienteUsuarioId(), atendimento.getPrestadorNome()
                + (concluir ? " marcou seu serviço como concluído." : " confirmou a contratação para " + data + " às " + horario + "."), "/notificacoes");
    }
}
