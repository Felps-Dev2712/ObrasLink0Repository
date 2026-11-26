package com.obraslink.service;

import com.obraslink.model.PapelUsuario;
import com.obraslink.model.Plano;
import com.obraslink.model.Prestador;
import com.obraslink.repository.CertificacaoRepository;
import com.obraslink.repository.PrestadorRepository;
import com.obraslink.repository.ServicoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Catálogo de planos do prestador e as travas de limite de cada um (serviços, certificações,
 * raio de atendimento e fotos por serviço). A troca de plano é imediata (sem gateway de
 * pagamento real: é uma simulação, adequada ao escopo acadêmico deste projeto).
 */
@Service
@RequiredArgsConstructor
public class PlanoService {

    private final AcessoService acessoService;
    private final PerfilAtivoService perfilAtivoService;
    private final PrestadorRepository prestadorRepository;
    private final ServicoRepository servicoRepository;
    private final CertificacaoRepository certificacaoRepository;

    public List<Plano> todos() {
        return List.of(Plano.values());
    }

    @Transactional(readOnly = true)
    public Prestador prestadorAtual() {
        var u = acessoService.getUsuarioAtualOrThrow();
        if (!Boolean.TRUE.equals(u.getAtivo()) || !u.getPapeis().contains(PapelUsuario.PRESTADOR)
                || perfilAtivoService.atual() != PapelUsuario.PRESTADOR) {
            throw new IllegalArgumentException("Ative o perfil de prestador para gerenciar seu plano.");
        }
        return prestadorRepository.findByUsuarioId(u.getId())
                .orElseThrow(() -> new IllegalArgumentException("Cadastro profissional não encontrado."));
    }

    @Transactional
    public Plano assinar(Plano novoPlano) {
        if (novoPlano == null) throw new IllegalArgumentException("Selecione um plano válido.");
        Prestador prestador = prestadorAtual();
        prestador.setPlano(novoPlano);
        prestadorRepository.save(prestador);
        return novoPlano;
    }

    // ---------- Travas de limite por plano ----------

    /** Lança erro amigável se o prestador já estiver no limite de serviços ativos do plano atual. */
    @Transactional(readOnly = true)
    public void exigirLimiteServicos(Prestador prestador) {
        int limite = prestador.getPlano().getMaxServicosAtivos();
        if (limite == Integer.MAX_VALUE) return;
        long ativos = servicoRepository.findByPrestadorIdAndDisponivel(prestador.getId(), true).size();
        if (ativos >= limite) {
            throw new IllegalArgumentException("Seu plano " + prestador.getPlano().getNome() + " permite até " + limite
                    + " serviços ativos no catálogo. Assine um plano superior para cadastrar mais.");
        }
    }

    /** Lança erro amigável se o prestador já estiver no limite de certificações do plano atual. */
    @Transactional(readOnly = true)
    public void exigirLimiteCertificacoes(Long prestadorId, Plano plano) {
        int limite = plano.getMaxCertificacoesAtivas();
        if (limite == Integer.MAX_VALUE) return;
        long atuais = certificacaoRepository.findByPrestadorIdOrderByCriadoEmDesc(prestadorId).size();
        if (atuais >= limite) {
            throw new IllegalArgumentException("Seu plano " + plano.getNome() + " permite até " + limite
                    + " certificações simultâneas. Assine um plano superior para enviar mais.");
        }
    }

}
