package com.obraslink.service;

import com.obraslink.dto.OrcamentoFormularioRequest;
import com.obraslink.model.*;
import com.obraslink.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OrcamentoServiceTest {
    private final AcessoService acesso = mock(AcessoService.class);
    private final PerfilAtivoService perfil = mock(PerfilAtivoService.class);
    private final ClienteRepository clientes = mock(ClienteRepository.class);
    private final PrestadorRepository prestadores = mock(PrestadorRepository.class);
    private final SolicitacaoOrcamentoRepository solicitacoes = mock(SolicitacaoOrcamentoRepository.class);
    private final SolicitacaoOrcamentoItemRepository itens = mock(SolicitacaoOrcamentoItemRepository.class);
    private final SolicitacaoOrcamentoAnexoRepository anexosRepo = mock(SolicitacaoOrcamentoAnexoRepository.class);
    private final OrcamentoAnexoService anexoService = mock(OrcamentoAnexoService.class);
    private final AtendimentoService atendimentoService = mock(AtendimentoService.class);
    private final OrcamentoService service = new OrcamentoService(acesso, perfil, clientes, prestadores,
            solicitacoes, itens, anexosRepo, anexoService, atendimentoService);

    private Usuario usuario;
    private Categoria pintor;
    private Prestador p1, p2;
    private Endereco enderecoCadastro;

    @BeforeEach
    void preparar() {
        usuario = Usuario.builder().id(1L).papeis(Set.of(PapelUsuario.CLIENTE)).ativo(true).build();
        pintor = Categoria.builder().id(5L).nome("Pintor").build();
        p1 = Prestador.builder().id(10L).usuarioId(20L).nome("Prestador Um").categoria(pintor).ativo(true).build();
        p2 = Prestador.builder().id(11L).usuarioId(21L).nome("Prestador Dois").categoria(pintor).ativo(true).build();
        when(acesso.getUsuarioAtualOrThrow()).thenReturn(usuario);
        when(perfil.atual()).thenReturn(PapelUsuario.CLIENTE);
        enderecoCadastro = Endereco.builder().id(50L).cep("80000-000").logradouro("Rua do Cadastro").numero("10")
                .bairro("Centro").cidade("Curitiba").estado("PR").build();
        when(clientes.findByUsuarioId(1L)).thenReturn(Optional.of(Cliente.builder().nome("Cliente Teste").endereco(enderecoCadastro).build()));
        when(solicitacoes.save(any(SolicitacaoOrcamento.class))).thenAnswer(inv -> {
            SolicitacaoOrcamento s = inv.getArgument(0);
            if (s.getId() == null) s.setId(100L);
            return s;
        });
        when(itens.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));
        when(itens.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void iniciarCriaSolicitacaoEUmItemPorPrestadorDaMesmaCategoria() {
        when(prestadores.findAllById(List.of(10L, 11L))).thenReturn(List.of(p1, p2));
        Long id = service.iniciar(List.of(10L, 11L));
        assertEquals(100L, id);
        verify(solicitacoes).save(argThat(s -> s.getCategoriaId().equals(5L) && s.getCategoriaNome().equals("Pintor") && s.getClienteUsuarioId().equals(1L)));
        verify(itens).saveAll(argThat((List<SolicitacaoOrcamentoItem> lista) -> lista.size() == 2
                && lista.stream().anyMatch(i -> i.getPrestadorId().equals(10L)) && lista.stream().anyMatch(i -> i.getPrestadorId().equals(11L))));
    }

    @Test
    void iniciarRejeitaListaVaziaECategoriasDiferentes() {
        assertThrows(IllegalArgumentException.class, () -> service.iniciar(List.of()));
        assertThrows(IllegalArgumentException.class, () -> service.iniciar(null));

        Categoria eletricista = Categoria.builder().id(6L).nome("Eletricista").build();
        Prestador outraCategoria = Prestador.builder().id(12L).usuarioId(22L).nome("Outro").categoria(eletricista).ativo(true).build();
        when(prestadores.findAllById(List.of(10L, 12L))).thenReturn(List.of(p1, outraCategoria));
        var erro = assertThrows(IllegalArgumentException.class, () -> service.iniciar(List.of(10L, 12L)));
        assertTrue(erro.getMessage().contains("mesma categoria"), erro.getMessage());
    }

    @Test
    void iniciarRejeitaPrestadorInativoOuAutocontratacao() {
        p1.setAtivo(false);
        when(prestadores.findAllById(List.of(10L))).thenReturn(List.of(p1));
        assertThrows(IllegalArgumentException.class, () -> service.iniciar(List.of(10L)));

        p1.setAtivo(true);
        p1.setUsuarioId(1L); // o próprio cliente logado
        when(prestadores.findAllById(List.of(10L))).thenReturn(List.of(p1));
        assertThrows(IllegalArgumentException.class, () -> service.iniciar(List.of(10L)));
    }

    private SolicitacaoOrcamento solicitacaoPintor() {
        SolicitacaoOrcamento s = new SolicitacaoOrcamento();
        s.setId(100L);
        s.setClienteUsuarioId(1L);
        s.setCategoriaId(5L);
        s.setCategoriaNome("Pintor");
        when(solicitacoes.findByIdAndClienteUsuarioId(100L, 1L)).thenReturn(Optional.of(s));
        return s;
    }

    private SolicitacaoOrcamentoItem item(Long itemId, Long prestadorId, String nome) {
        return SolicitacaoOrcamentoItem.builder().id(itemId).solicitacaoId(100L).prestadorId(prestadorId).prestadorNome(nome).mensagem("").build();
    }

    private static Endereco enderecoCustom() {
        return Endereco.builder().cep("04000-000").logradouro("Av. Brasil").numero("500")
                .bairro("Jardim").cidade("São Paulo").estado("sp").build();
    }

    @Test
    void salvarFormularioExigeDescricaoECamposObrigatoriosDaCategoria() {
        solicitacaoPintor();
        OrcamentoFormularioRequest form = new OrcamentoFormularioRequest();
        var erro1 = assertThrows(IllegalArgumentException.class, () -> service.salvarFormulario(100L, form, null));
        assertTrue(erro1.getMessage().contains("Descreva"), erro1.getMessage());

        form.setDescricaoGeral("Pintar a sala");
        when(itens.findBySolicitacaoIdOrderByIdAsc(100L)).thenReturn(List.of());
        var erro2 = assertThrows(IllegalArgumentException.class, () -> service.salvarFormulario(100L, form, null));
        assertTrue(erro2.getMessage().contains("Área aproximada"), erro2.getMessage());
    }

    @Test
    void salvarFormularioExigeEnderecoCadastradoOuOutroInformado() {
        solicitacaoPintor();
        when(clientes.findByUsuarioId(1L)).thenReturn(Optional.of(Cliente.builder().nome("Sem endereço").build()));
        OrcamentoFormularioRequest form = new OrcamentoFormularioRequest();
        form.setDescricaoGeral("Pintar a sala");
        form.setCampos(Map.of("areaM2", "30", "tipoSuperficie", "a", "quantidadeComodos", "1", "estadoSuperficie", "a", "necessitaPreparacao", "a"));
        var erro = assertThrows(IllegalArgumentException.class, () -> service.salvarFormulario(100L, form, null));
        assertTrue(erro.getMessage().contains("endereço"), erro.getMessage());

        form.setUsarEnderecoCadastrado(false);
        form.setEnderecoExecucao(new Endereco()); // faltando campos
        var erro2 = assertThrows(IllegalArgumentException.class, () -> service.salvarFormulario(100L, form, null));
        assertTrue(erro2.getMessage().contains("endereço"), erro2.getMessage());
    }

    @Test
    void salvarFormularioUsaOEnderecoCadastradoPorPadraoSemAlterarOCadastro() {
        SolicitacaoOrcamento solicitacao = solicitacaoPintor();
        OrcamentoFormularioRequest form = new OrcamentoFormularioRequest();
        form.setDescricaoGeral("Pintar a sala");
        form.setCampos(Map.of("areaM2", "30", "tipoSuperficie", "Alvenaria", "quantidadeComodos", "2", "estadoSuperficie", "Boa", "necessitaPreparacao", "Não"));
        List<SolicitacaoOrcamentoItem> lista = List.of(item(1L, 10L, "Prestador Um"));
        when(itens.findBySolicitacaoIdOrderByIdAsc(100L)).thenReturn(lista);
        when(anexosRepo.countBySolicitacaoId(100L)).thenReturn(0L);

        service.salvarFormulario(100L, form, null);

        assertNotSame(enderecoCadastro, solicitacao.getEnderecoExecucao(), "a solicitação precisa ter a própria cópia, não a mesma linha do cadastro");
        assertEquals("Rua do Cadastro, 10 - Centro, Curitiba/PR", solicitacao.getEnderecoExecucao().getEnderecoCompleto());
        assertEquals("Rua do Cadastro", enderecoCadastro.getLogradouro(), "o endereço do perfil não pode mudar");
        verify(itens).saveAll(argThat((List<SolicitacaoOrcamentoItem> salvos) ->
                salvos.get(0).getMensagem().contains("Local: Rua do Cadastro, 10 - Centro, Curitiba/PR")));
    }

    @Test
    void salvarFormularioUsaOutroEnderecoSoNestaSolicitacao() {
        SolicitacaoOrcamento solicitacao = solicitacaoPintor();
        OrcamentoFormularioRequest form = new OrcamentoFormularioRequest();
        form.setUsarEnderecoCadastrado(false);
        form.setEnderecoExecucao(enderecoCustom());
        form.setDescricaoGeral("Pintar a fachada");
        form.setCampos(Map.of("areaM2", "80", "tipoSuperficie", "Alvenaria", "quantidadeComodos", "N/A", "estadoSuperficie", "Boa", "necessitaPreparacao", "Não"));
        List<SolicitacaoOrcamentoItem> lista = List.of(item(1L, 10L, "Prestador Um"));
        when(itens.findBySolicitacaoIdOrderByIdAsc(100L)).thenReturn(lista);
        when(anexosRepo.countBySolicitacaoId(100L)).thenReturn(0L);

        service.salvarFormulario(100L, form, null);

        assertEquals("Av. Brasil, 500 - Jardim, São Paulo/SP", solicitacao.getEnderecoExecucao().getEnderecoCompleto());
        assertEquals("Rua do Cadastro", enderecoCadastro.getLogradouro(), "o endereço do perfil não pode mudar");
        verify(itens).saveAll(argThat((List<SolicitacaoOrcamentoItem> salvos) ->
                salvos.get(0).getMensagem().contains("Local: Av. Brasil, 500 - Jardim, São Paulo/SP")));
    }

    @Test
    void salvarFormularioReaproveitaALinhaDeEnderecoJaExistenteAoReeditar() {
        SolicitacaoOrcamento solicitacao = solicitacaoPintor();
        Endereco existente = Endereco.builder().id(777L).cep("00000-000").logradouro("Antigo").numero("1").bairro("B").cidade("C").estado("PR").build();
        solicitacao.setEnderecoExecucao(existente);
        OrcamentoFormularioRequest form = new OrcamentoFormularioRequest();
        form.setDescricaoGeral("Pintar a sala");
        form.setCampos(Map.of("areaM2", "30", "tipoSuperficie", "a", "quantidadeComodos", "1", "estadoSuperficie", "a", "necessitaPreparacao", "a"));
        when(itens.findBySolicitacaoIdOrderByIdAsc(100L)).thenReturn(List.of());
        when(anexosRepo.countBySolicitacaoId(100L)).thenReturn(0L);

        service.salvarFormulario(100L, form, null);

        assertSame(existente, solicitacao.getEnderecoExecucao(), "reaproveita a mesma linha em vez de criar outra ao editar de novo");
        assertEquals("Rua do Cadastro", solicitacao.getEnderecoExecucao().getLogradouro());
    }

    @Test
    void salvarFormularioGeraMensagemComCamposEspecificosEAnexos() {
        solicitacaoPintor();
        OrcamentoFormularioRequest form = new OrcamentoFormularioRequest();
        form.setDescricaoGeral("Pintar a sala");
        form.setUrgencia(SolicitacaoOrcamento.Urgencia.ALTA);
        form.setCampos(Map.of(
                "areaM2", "30",
                "tipoSuperficie", "Alvenaria",
                "quantidadeComodos", "2",
                "estadoSuperficie", "Com trincas",
                "necessitaPreparacao", "Sim"
        ));
        List<SolicitacaoOrcamentoItem> lista = List.of(item(1L, 10L, "Prestador Um"), item(2L, 11L, "Prestador Dois"));
        when(itens.findBySolicitacaoIdOrderByIdAsc(100L)).thenReturn(lista);
        when(anexosRepo.countBySolicitacaoId(100L)).thenReturn(0L);
        when(anexoService.armazenar(any())).thenReturn("abc123.jpg");

        MockMultipartFile foto = new MockMultipartFile("anexos", "foto.jpg", "image/jpeg", new byte[]{1, 2, 3});
        service.salvarFormulario(100L, form, List.of(foto));

        verify(anexosRepo).saveAll(argThat((List<SolicitacaoOrcamentoAnexo> novos) -> novos.size() == 1 && novos.get(0).getChave().equals("abc123.jpg")));
        verify(itens).saveAll(argThat((List<SolicitacaoOrcamentoItem> salvos) -> salvos.stream().allMatch(i ->
                i.getMensagem().contains("Pintor") && i.getMensagem().contains("Área aproximada (m²): 30")
                        && i.getMensagem().contains("Urgência: Urgente") && i.getMensagem().contains("Anexei 1 arquivo"))));
    }

    @Test
    void salvarFormularioRespeitaLimiteDeAnexos() {
        solicitacaoPintor();
        OrcamentoFormularioRequest form = new OrcamentoFormularioRequest();
        form.setDescricaoGeral("Pintar a sala");
        form.setCampos(Map.of("areaM2", "30", "tipoSuperficie", "a", "quantidadeComodos", "1", "estadoSuperficie", "a", "necessitaPreparacao", "a"));
        when(anexosRepo.countBySolicitacaoId(100L)).thenReturn(6L);
        when(itens.findBySolicitacaoIdOrderByIdAsc(100L)).thenReturn(List.of());
        MockMultipartFile foto = new MockMultipartFile("anexos", "foto.jpg", "image/jpeg", new byte[]{1});
        assertThrows(IllegalArgumentException.class, () -> service.salvarFormulario(100L, form, List.of(foto)));
    }

    @Test
    void confirmarEnvioAplicaEdicoesEEnviaIndividualmente() {
        SolicitacaoOrcamento s = solicitacaoPintor();
        SolicitacaoOrcamentoItem i1 = item(1L, 10L, "Prestador Um");
        i1.setMensagem("mensagem original 1");
        SolicitacaoOrcamentoItem i2 = item(2L, 11L, "Prestador Dois");
        i2.setMensagem("mensagem original 2");
        when(itens.findBySolicitacaoIdOrderByIdAsc(100L)).thenReturn(List.of(i1, i2));
        when(atendimentoService.contatarComMensagem(10L, "mensagem editada")).thenReturn("https://wa.me/551100000000?text=x");
        when(atendimentoService.contatarComMensagem(11L, "mensagem original 2")).thenReturn("https://wa.me/551100000001?text=y");

        var resultados = service.confirmarEnvio(100L, Map.of(1L, "mensagem editada"));

        assertEquals(2, resultados.size());
        assertEquals("mensagem editada", i1.getMensagem());
        assertEquals(SolicitacaoOrcamento.Status.ENVIADA, s.getStatus());
        verify(atendimentoService).contatarComMensagem(10L, "mensagem editada");
        verify(atendimentoService).contatarComMensagem(11L, "mensagem original 2");
    }

    @Test
    void confirmarEnvioRejeitaQuandoFaltaMensagem() {
        solicitacaoPintor();
        SolicitacaoOrcamentoItem semMensagem = item(1L, 10L, "Prestador Um");
        when(itens.findBySolicitacaoIdOrderByIdAsc(100L)).thenReturn(List.of(semMensagem));
        assertThrows(IllegalArgumentException.class, () -> service.confirmarEnvio(100L, Map.of()));
        verifyNoInteractions(atendimentoService);
    }

    @Test
    void excluirAnexoApagaRegistroEArquivo() {
        solicitacaoPintor();
        SolicitacaoOrcamentoAnexo anexo = SolicitacaoOrcamentoAnexo.builder().id(1L).solicitacaoId(100L).chave("abc.jpg").build();
        when(anexosRepo.findByIdAndSolicitacaoId(1L, 100L)).thenReturn(Optional.of(anexo));
        service.excluirAnexo(100L, 1L);
        verify(anexosRepo).delete(anexo);
        verify(anexoService).excluirArquivo("abc.jpg");
    }

    @Test
    void acessoRestritoAoClienteDonoDaSolicitacao() {
        when(solicitacoes.findByIdAndClienteUsuarioId(999L, 1L)).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class, () -> service.consultarFormulario(999L));
    }

    @Test
    void exigePerfilDeClienteAtivo() {
        when(perfil.atual()).thenReturn(PapelUsuario.PRESTADOR);
        assertThrows(IllegalArgumentException.class, () -> service.iniciar(List.of(10L)));
    }
}
