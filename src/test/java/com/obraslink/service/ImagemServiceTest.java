package com.obraslink.service;

import com.obraslink.model.PapelUsuario;
import com.obraslink.model.Prestador;
import com.obraslink.model.ServicoFoto;
import com.obraslink.model.Usuario;
import com.obraslink.repository.PrestadorRepository;
import com.obraslink.repository.ServicoFotoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

class ImagemServiceTest {
    private static final byte[] JPG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0, 0, 0, 0, 0, 0, 0};
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};
    private static final byte[] WEBP = {'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P'};

    @TempDir Path pasta;
    private final AcessoService acesso = mock(AcessoService.class);
    private final PrestadorRepository prestadores = mock(PrestadorRepository.class);
    private final ServicoFotoRepository fotos = mock(ServicoFotoRepository.class);
    private final PerfilAtivoService perfil = mock(PerfilAtivoService.class);
    private ImagemService service;
    private Prestador prestador;

    @BeforeEach
    void preparar() {
        service = new ImagemService(acesso, prestadores, fotos, perfil, pasta.toString());
        Usuario usuario = Usuario.builder().id(7L).email("p@x.com").senha("x").ativo(true).papeis(Set.of(PapelUsuario.PRESTADOR)).build();
        prestador = Prestador.builder().id(30L).nome("Fornecedor").usuarioId(7L).build();
        when(acesso.getUsuarioAtualOrThrow()).thenReturn(usuario);
        when(perfil.atual()).thenReturn(PapelUsuario.PRESTADOR);
        when(prestadores.findByUsuarioId(7L)).thenReturn(Optional.of(prestador));
    }

    private static MockMultipartFile arquivo(String nome, byte[] conteudo) {
        return new MockMultipartFile("arquivo", nome, "image/jpeg", conteudo);
    }

    @Test
    void reconheceJpgPngEWebpPeloConteudo() {
        assertEquals("jpg", service.validar(arquivo("a.bin", JPG)));
        assertEquals("png", service.validar(arquivo("a.jpg", PNG)));
        assertEquals("webp", service.validar(arquivo("a.png", WEBP)));
    }

    @Test
    void recusaArquivoComExtensaoDeImagemMasConteudoDiferente() {
        var falso = arquivo("foto.png", "<script>alert(1)</script>".getBytes());
        var erro = assertThrows(IllegalArgumentException.class, () -> service.validar(falso));
        assertTrue(erro.getMessage().contains("JPG, PNG ou WebP"));
    }

    @Test
    void recusaArquivoVazioEAcimaDe5Mb() {
        assertThrows(IllegalArgumentException.class, () -> service.validar(arquivo("v.jpg", new byte[0])));
        byte[] grande = new byte[(int) ImagemService.TAMANHO_MAXIMO + 1];
        System.arraycopy(JPG, 0, grande, 0, JPG.length);
        var erro = assertThrows(IllegalArgumentException.class, () -> service.validar(arquivo("g.jpg", grande)));
        assertTrue(erro.getMessage().contains("5 MB"));
    }

    @Test
    void chaveDeExibicaoAceitaSoONomeGeradoPeloSistema() {
        assertTrue(service.arquivo("../application.properties").isEmpty());
        assertTrue(service.arquivo("..%2Fsegredo.png").isEmpty());
        assertTrue(service.arquivo("nao-e-hex.png").isEmpty());
        assertTrue(service.arquivo(null).isEmpty());
        String chave = service.armazenar(arquivo("ok.png", PNG));
        assertTrue(chave.matches("[a-f0-9]{32}\\.png"));
        assertTrue(service.arquivo(chave).isPresent());
    }

    @Test
    void atualizaFotoDePerfilEApagaAAnterior() throws Exception {
        String antiga = service.armazenar(arquivo("antiga.jpg", JPG));
        prestador.setFotoPerfilChave(antiga);

        service.atualizarImagemPerfil(ImagemService.Tipo.FOTO_PERFIL, arquivo("nova.png", PNG));

        assertNotEquals(antiga, prestador.getFotoPerfilChave());
        assertTrue(prestador.getFotoPerfilChave().endsWith(".png"));
        assertFalse(Files.exists(pasta.resolve("imagens").resolve(antiga)));
        assertTrue(service.arquivo(prestador.getFotoPerfilChave()).isPresent());
    }

    @Test
    void bannerExigePerfilDePrestadorAtivo() {
        when(perfil.atual()).thenReturn(PapelUsuario.CLIENTE);
        assertThrows(IllegalArgumentException.class,
                () -> service.atualizarImagemPerfil(ImagemService.Tipo.BANNER, arquivo("b.jpg", JPG)));
        assertNull(prestador.getBannerChave());
    }

    @Test
    void removeBannerELimpaAChave() {
        prestador.setBannerChave(service.armazenar(arquivo("b.jpg", JPG)));
        service.removerImagemPerfil(ImagemService.Tipo.BANNER);
        assertNull(prestador.getBannerChave());
    }

    @Test
    void fotosDoServicoSoParaQuemPodeGerenciar() {
        when(acesso.podeGerenciarServico(5L)).thenReturn(false);
        assertThrows(IllegalArgumentException.class,
                () -> service.adicionarFotosServico(5L, List.of(arquivo("s.jpg", JPG))));
        verify(fotos, never()).saveAll(anyList());
    }

    @Test
    void respeitaOLimiteDeFotosPorServicoSemGravarNada() throws Exception {
        when(acesso.podeGerenciarServico(5L)).thenReturn(true);
        when(fotos.countByServicoId(5L)).thenReturn(5L);
        assertThrows(IllegalArgumentException.class,
                () -> service.adicionarFotosServico(5L, List.of(arquivo("1.jpg", JPG), arquivo("2.jpg", JPG))));
        verify(fotos, never()).saveAll(anyList());
        Path imagens = pasta.resolve("imagens");
        assertTrue(!Files.exists(imagens) || Files.list(imagens).findAny().isEmpty());
    }

    @Test
    void naoGravaNenhumaFotoSeUmaDelasForInvalida() {
        when(acesso.podeGerenciarServico(5L)).thenReturn(true);
        when(fotos.countByServicoId(5L)).thenReturn(0L);
        assertThrows(IllegalArgumentException.class,
                () -> service.adicionarFotosServico(5L, List.of(arquivo("ok.jpg", JPG), arquivo("ruim.jpg", "texto".getBytes()))));
        verify(fotos, never()).saveAll(anyList());
    }

    @Test
    void salvaFotosValidasEIgnoraCamposVazios() {
        when(acesso.podeGerenciarServico(5L)).thenReturn(true);
        when(fotos.countByServicoId(5L)).thenReturn(1L);
        int total = service.adicionarFotosServico(5L, List.of(arquivo("a.jpg", JPG), arquivo("vazio.jpg", new byte[0]), arquivo("b.webp", WEBP)));
        assertEquals(2, total);
        verify(fotos).saveAll(argThatSize(2));
    }

    @Test
    void naoExcluiFotoDeOutroServico() {
        when(acesso.podeGerenciarServico(5L)).thenReturn(true);
        when(fotos.findByIdAndServicoId(99L, 5L)).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class, () -> service.excluirFotoServico(5L, 99L));
        verify(fotos, never()).delete(any(ServicoFoto.class));
    }

    @Test
    void agrupaFotosPorServicoNumaConsulta() {
        when(fotos.findByServicoIdInOrderByIdAsc(List.of(1L, 2L)))
                .thenReturn(List.of(new ServicoFoto(1L, "a"), new ServicoFoto(2L, "b"), new ServicoFoto(1L, "c")));
        var mapa = service.fotosPorServico(List.of(1L, 2L));
        assertEquals(2, mapa.get(1L).size());
        assertEquals("a", mapa.get(1L).get(0).getChave());
        assertTrue(service.fotosPorServico(List.of()).isEmpty());
        verify(fotos, times(1)).findByServicoIdInOrderByIdAsc(any());
    }

    @Test
    void clienteTrocaAFotoEAAnteriorEhApagada() {
        var clientes = mock(com.obraslink.repository.ClienteRepository.class);
        var comCliente = new ImagemService(acesso, prestadores, fotos, perfil, clientes, pasta.toString());
        Usuario usuario = Usuario.builder().id(8L).email("c@x.com").senha("x").ativo(true).papeis(Set.of(PapelUsuario.CLIENTE)).build();
        com.obraslink.model.Cliente cliente = com.obraslink.model.Cliente.builder().id(40L).nome("Cliente").build();
        when(acesso.getUsuarioAtualOrThrow()).thenReturn(usuario);
        when(perfil.atual()).thenReturn(PapelUsuario.CLIENTE);
        when(clientes.findByUsuarioId(8L)).thenReturn(Optional.of(cliente));
        String antiga = comCliente.armazenar(arquivo("antiga.jpg", JPG));
        cliente.setFotoPerfilChave(antiga);

        comCliente.atualizarFotoCliente(arquivo("nova.webp", WEBP));
        assertTrue(cliente.getFotoPerfilChave().endsWith(".webp"));
        assertFalse(Files.exists(pasta.resolve("imagens").resolve(antiga)));

        comCliente.removerFotoCliente();
        assertNull(cliente.getFotoPerfilChave());
    }

    @Test
    void fotoDeClienteExigePerfilDeClienteAtivo() {
        var clientes = mock(com.obraslink.repository.ClienteRepository.class);
        var comCliente = new ImagemService(acesso, prestadores, fotos, perfil, clientes, pasta.toString());
        // usuário do @BeforeEach só tem o papel PRESTADOR
        assertThrows(IllegalArgumentException.class, () -> comCliente.atualizarFotoCliente(arquivo("a.jpg", JPG)));
        verifyNoInteractions(clientes);
    }

    @SuppressWarnings("unchecked")
    private static Iterable<ServicoFoto> argThatSize(int tamanho) {
        return org.mockito.ArgumentMatchers.argThat(lista -> lista != null && ((List<ServicoFoto>) lista).size() == tamanho);
    }
}
