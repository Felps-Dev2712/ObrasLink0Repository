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

    private static byte[] imagemReal(String formato, int largura, int altura) throws Exception {
        var imagem = new java.awt.image.BufferedImage(largura, altura, java.awt.image.BufferedImage.TYPE_INT_RGB);
        var saida = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(imagem, formato, saida);
        return saida.toByteArray();
    }

    @Test
    void capaNoTamanhoMinimoOuMaiorEhAceitaSemAlterarOArquivo() throws Exception {
        byte[] png = imagemReal("png", 1600, 400);
        service.atualizarImagemPerfil(ImagemService.Tipo.BANNER, arquivo("capa.png", png));
        assertNotNull(prestador.getBannerChave());
        assertArrayEquals(png, Files.readAllBytes(service.arquivo(prestador.getBannerChave()).orElseThrow()), "sem recompressão");

        service.atualizarImagemPerfil(ImagemService.Tipo.BANNER, arquivo("capa.jpg", imagemReal("jpg", 1200, 300)));
        assertTrue(prestador.getBannerChave().endsWith(".jpg"));
    }

    @Test
    void capaMenorQue1200x300EhRecusadaComAsDimensoesNaMensagem() throws Exception {
        var erro = assertThrows(IllegalArgumentException.class,
                () -> service.atualizarImagemPerfil(ImagemService.Tipo.BANNER, arquivo("pequena.png", imagemReal("png", 800, 600))));
        assertTrue(erro.getMessage().contains("1200 × 300") && erro.getMessage().contains("800 × 600"), erro.getMessage());
        assertThrows(IllegalArgumentException.class,
                () -> service.atualizarImagemPerfil(ImagemService.Tipo.BANNER, arquivo("baixa.png", imagemReal("png", 2000, 250))));
        assertNull(prestador.getBannerChave());
        Path imagens = pasta.resolve("imagens");
        assertTrue(!Files.exists(imagens) || Files.list(imagens).findAny().isEmpty(), "nada gravado em disco");
    }

    @Test
    void capaComCabecalhoIlegivelEhRecusada() {
        assertThrows(IllegalArgumentException.class,
                () -> service.atualizarImagemPerfil(ImagemService.Tipo.BANNER, arquivo("falsa.jpg", JPG)));
        assertNull(prestador.getBannerChave());
    }

    @Test
    void fotoDePerfilNaoTemExigenciaDeTamanhoDeCapa() throws Exception {
        service.atualizarImagemPerfil(ImagemService.Tipo.FOTO_PERFIL, arquivo("rosto.png", imagemReal("png", 200, 200)));
        assertNotNull(prestador.getFotoPerfilChave());
    }

    @Test
    void leDimensoesDosTresTiposDeWebp() {
        byte[] vp8x = webp("VP8X");
        int l = 1600 - 1, a = 400 - 1;
        vp8x[24] = (byte) l; vp8x[25] = (byte) (l >> 8); vp8x[26] = (byte) (l >> 16);
        vp8x[27] = (byte) a; vp8x[28] = (byte) (a >> 8); vp8x[29] = (byte) (a >> 16);
        assertArrayEquals(new int[]{1600, 400}, ImagemService.dimensoesWebp(vp8x).orElseThrow());

        byte[] vp8 = webp("VP8 ");
        vp8[23] = (byte) 0x9D; vp8[24] = 0x01; vp8[25] = 0x2A;
        vp8[26] = (byte) (1280 & 0xFF); vp8[27] = (byte) (1280 >> 8); vp8[28] = (byte) (320 & 0xFF); vp8[29] = (byte) (320 >> 8);
        assertArrayEquals(new int[]{1280, 320}, ImagemService.dimensoesWebp(vp8).orElseThrow());

        byte[] vp8l = webp("VP8L");
        int w = 1200 - 1, h = 300 - 1;
        long bits = w | ((long) h << 14);
        vp8l[20] = 0x2F; vp8l[21] = (byte) bits; vp8l[22] = (byte) (bits >> 8); vp8l[23] = (byte) (bits >> 16); vp8l[24] = (byte) (bits >> 24);
        assertArrayEquals(new int[]{1200, 300}, ImagemService.dimensoesWebp(vp8l).orElseThrow());

        assertTrue(ImagemService.dimensoesWebp(webp("XXXX")).isEmpty());
        assertTrue(ImagemService.dimensoesWebp(new byte[10]).isEmpty());
    }

    private static byte[] webp(String bloco) {
        byte[] b = new byte[30];
        System.arraycopy("RIFF".getBytes(), 0, b, 0, 4);
        System.arraycopy("WEBP".getBytes(), 0, b, 8, 4);
        System.arraycopy(bloco.getBytes(), 0, b, 12, 4);
        return b;
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
