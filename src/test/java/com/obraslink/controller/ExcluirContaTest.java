package com.obraslink.controller;

import com.obraslink.service.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ExcluirContaTest {
    private final ContaService contaService = mock(ContaService.class);
    private final ClienteController controller = new ClienteController(
            mock(ClienteService.class), mock(PrestadorService.class),
            mock(CategoriaService.class), mock(AcessoService.class), contaService);

    @AfterEach
    void limparContexto() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void excluiContaEEncerraSessao() {
        var request = new MockHttpServletRequest();
        var session = (MockHttpSession) request.getSession();
        var authentication = new UsernamePasswordAuthenticationToken("usuario", "senha", List.of());
        SecurityContextHolder.getContext().setAuthentication(authentication);

        assertEquals("redirect:/login?contaExcluida", controller.deleteOwnAccount(
                request, new MockHttpServletResponse(), authentication, new RedirectAttributesModelMap()));

        verify(contaService).excluirMinhaConta();
        assertTrue(session.isInvalid());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void preservaSessaoEInformaFalhaQuandoExclusaoFalha() {
        doThrow(new DataIntegrityViolationException("falha")).when(contaService).excluirMinhaConta();
        var request = new MockHttpServletRequest();
        var session = (MockHttpSession) request.getSession();
        var attributes = new RedirectAttributesModelMap();
        var authentication = new UsernamePasswordAuthenticationToken("usuario", "senha", List.of());
        SecurityContextHolder.getContext().setAuthentication(authentication);

        assertEquals("redirect:/clientes", controller.deleteOwnAccount(
                request, new MockHttpServletResponse(), authentication, attributes));

        assertFalse(session.isInvalid());
        assertSame(authentication, SecurityContextHolder.getContext().getAuthentication());
        assertTrue(attributes.getFlashAttributes().containsKey("mensagemErro"));
    }
}
