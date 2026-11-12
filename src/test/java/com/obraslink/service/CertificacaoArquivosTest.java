package com.obraslink.service;

import com.obraslink.model.Certificacao;
import com.obraslink.repository.CertificacaoRepository;
import com.obraslink.repository.PrestadorRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Exclusão de conta: os comprovantes das certificações também saem do disco. */
class CertificacaoArquivosTest {
    @TempDir Path uploads;

    private static Certificacao certificacao(String chave) {
        Certificacao c = new Certificacao();
        c.setDocumentoChave(chave);
        return c;
    }

    @Test
    void apagaOsComprovantesDoPrestadorSemTocarEmOutrosArquivosNemSairDaPasta() throws Exception {
        CertificacaoRepository certificacoes = mock(CertificacaoRepository.class);
        CertificacaoService service = new CertificacaoService(mock(AcessoService.class), mock(PrestadorRepository.class), certificacoes, mock(PerfilAtivoService.class));
        ReflectionTestUtils.setField(service, "uploadDir", uploads.toString());
        Path doc1 = Files.writeString(uploads.resolve("a.pdf"), "x");
        Path doc2 = Files.writeString(uploads.resolve("b.png"), "x");
        Path deOutro = Files.writeString(uploads.resolve("outro.pdf"), "x");
        Path foraDaPasta = Files.writeString(uploads.getParent().resolve("fora-" + System.nanoTime() + ".pdf"), "x");
        when(certificacoes.findByPrestadorIdOrderByCriadoEmDesc(30L)).thenReturn(List.of(
                certificacao("a.pdf"), certificacao("b.png"), certificacao("../" + foraDaPasta.getFileName()), certificacao(null)));

        service.excluirDocumentosDoPrestadorAposConfirmar(30L);

        assertFalse(Files.exists(doc1));
        assertFalse(Files.exists(doc2));
        assertTrue(Files.exists(deOutro), "arquivo de outro prestador continua");
        assertTrue(Files.exists(foraDaPasta), "chave com ../ não sai da pasta de uploads");
        Files.deleteIfExists(foraDaPasta);
    }

    private CertificacaoService servico() {
        CertificacaoService service = new CertificacaoService(mock(AcessoService.class), mock(PrestadorRepository.class), mock(CertificacaoRepository.class), mock(PerfilAtivoService.class));
        ReflectionTestUtils.setField(service, "uploadDir", uploads.toString());
        return service;
    }

    @Test
    void comprovanteEhReconhecidoPeloConteudoIncluindoWebpENomeComExtensaoErrada() {
        var service = servico();
        byte[] pdf = "%PDF-1.4 conteudo".getBytes();
        byte[] jpg = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0, 0, 0, 0, 0, 0, 0};
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};
        byte[] webp = {'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P'};
        assertEquals(".pdf", service.validarDocumento(new org.springframework.mock.web.MockMultipartFile("d", "certificado.pdf", "application/pdf", pdf)));
        assertEquals(".jpg", service.validarDocumento(new org.springframework.mock.web.MockMultipartFile("d", "foto.jpeg", "image/jpeg", jpg)));
        assertEquals(".webp", service.validarDocumento(new org.springframework.mock.web.MockMultipartFile("d", "foto.webp", "image/webp", webp)));
        // PNG salvo com extensão .jpg (e tipo informado errado pelo navegador): vale o conteúdo
        assertEquals(".png", service.validarDocumento(new org.springframework.mock.web.MockMultipartFile("d", "print.jpg", "image/jpeg", png)));
    }

    @Test
    void mensagemDeTamanhoSoQuandoPassaDe10MbEMensagemDeFormatoSeparada() {
        var service = servico();
        byte[] grande = new byte[10 * 1024 * 1024 + 1];
        grande[0] = (byte) 0xFF; grande[1] = (byte) 0xD8; grande[2] = (byte) 0xFF;
        var erroTamanho = assertThrows(IllegalArgumentException.class,
                () -> service.validarDocumento(new org.springframework.mock.web.MockMultipartFile("d", "grande.jpg", "image/jpeg", grande)));
        assertTrue(erroTamanho.getMessage().contains("grande.jpg") && erroTamanho.getMessage().contains("10 MB"), erroTamanho.getMessage());

        byte[] limite = new byte[10 * 1024 * 1024];
        limite[0] = (byte) 0xFF; limite[1] = (byte) 0xD8; limite[2] = (byte) 0xFF;
        assertEquals(".jpg", service.validarDocumento(new org.springframework.mock.web.MockMultipartFile("d", "limite.jpg", "image/jpeg", limite)), "exatamente 10 MB é aceito");

        var erroFormato = assertThrows(IllegalArgumentException.class,
                () -> service.validarDocumento(new org.springframework.mock.web.MockMultipartFile("d", "nota.txt", "text/plain", "texto".getBytes())));
        assertTrue(erroFormato.getMessage().contains("nota.txt") && !erroFormato.getMessage().contains("10 MB"), erroFormato.getMessage());
    }

    @Test
    void semCertificacoesNaoFazNada() {
        CertificacaoRepository certificacoes = mock(CertificacaoRepository.class);
        CertificacaoService service = new CertificacaoService(mock(AcessoService.class), mock(PrestadorRepository.class), certificacoes, mock(PerfilAtivoService.class));
        ReflectionTestUtils.setField(service, "uploadDir", uploads.toString());
        when(certificacoes.findByPrestadorIdOrderByCriadoEmDesc(30L)).thenReturn(List.of());
        assertDoesNotThrow(() -> service.excluirDocumentosDoPrestadorAposConfirmar(30L));
    }
}
