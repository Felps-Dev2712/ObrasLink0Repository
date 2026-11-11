package com.obraslink.controller;

import com.obraslink.service.ImagemService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ImagemControllerTest {
    private final ImagemService imagens = mock(ImagemService.class);
    private final ImagemController controller = new ImagemController(imagens);

    @Test
    void retornoAceitaSoTelasConhecidas() {
        assertEquals("/clientes", ImagemController.retornoSeguro(null));
        assertEquals("/clientes", ImagemController.retornoSeguro("https://site-malicioso.com"));
        assertEquals("/clientes", ImagemController.retornoSeguro("//site-malicioso.com"));
        assertEquals("/clientes", ImagemController.retornoSeguro("/prestadores/1/editar"));
        assertEquals("/prestadores/42", ImagemController.retornoSeguro("/prestadores/42"));
        assertEquals("/clientes", ImagemController.retornoSeguro("/clientes"));
    }

    @Test
    void imagemInexistenteRetorna404() {
        when(imagens.arquivo("x")).thenReturn(Optional.empty());
        assertEquals(404, controller.exibir("x").getStatusCode().value());
    }

    @Test
    void erroDeValidacaoViraMensagemEVoltaParaAConta() {
        var arquivo = new MockMultipartFile("arquivo", "a.txt", "text/plain", "x".getBytes());
        doThrow(new IllegalArgumentException("Envie uma imagem JPG, PNG ou WebP."))
                .when(imagens).atualizarImagemPerfil(ImagemService.Tipo.FOTO_PERFIL, arquivo);
        var attrs = new RedirectAttributesModelMap();
        assertEquals("redirect:/clientes", controller.enviarImagemPerfil("foto", arquivo, "//fora", attrs));
        assertEquals("Envie uma imagem JPG, PNG ou WebP.", attrs.getFlashAttributes().get("mensagemErro"));
    }

    @Test
    void envioDeFotosDoServicoVoltaParaAEdicao() {
        var attrs = new RedirectAttributesModelMap();
        when(imagens.adicionarFotosServico(eq(5L), any())).thenReturn(2);
        assertEquals("redirect:/servicos/5/editar", controller.enviarFotosServico(5L, java.util.List.of(), attrs));
        assertEquals("2 fotos adicionadas.", attrs.getFlashAttributes().get("mensagem"));
    }
}
