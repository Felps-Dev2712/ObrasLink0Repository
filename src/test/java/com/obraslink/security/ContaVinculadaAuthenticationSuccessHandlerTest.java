package com.obraslink.security;

import com.obraslink.model.Usuario;
import com.obraslink.repository.UsuarioRepository;
import com.obraslink.service.ContaVinculadaService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ContaVinculadaAuthenticationSuccessHandlerTest {
    UsuarioRepository usuarios = mock(UsuarioRepository.class);
    ContaVinculadaService contaVinculadaService = mock(ContaVinculadaService.class);
    ContasVinculadasCookie cookie = mock(ContasVinculadasCookie.class);
    ContaVinculadaAuthenticationSuccessHandler handler =
            new ContaVinculadaAuthenticationSuccessHandler(usuarios, contaVinculadaService, cookie);

    @Test
    void aposLoginVinculaAContaESomaOTokenAoCookieSemApagarOsOutros() throws Exception {
        var usuario = Usuario.builder().id(3L).email("ana@exemplo.com").build();
        when(usuarios.findByEmail("ana@exemplo.com")).thenReturn(Optional.of(usuario));
        when(contaVinculadaService.vincular(3L)).thenReturn("token-novo");
        var auth = new UsernamePasswordAuthenticationToken("ana@exemplo.com", "senha");
        var request = new MockHttpServletRequest();
        var response = new MockHttpServletResponse();

        handler.onAuthenticationSuccess(request, response, auth);

        verify(contaVinculadaService).vincular(3L);
        verify(cookie).adicionar(request, response, "token-novo");
        assertEquals("/", response.getRedirectedUrl());
        assertTrue(response.getHeader("Set-Cookie").contains("obl_tchuros_login=1"),
                "marca que acabou de logar, pro mascote Tchuros aparecer na home");
    }

    @Test
    void naoFalhaSeOUsuarioLogadoNaoForEncontrado() throws Exception {
        when(usuarios.findByEmail("fantasma@exemplo.com")).thenReturn(Optional.empty());
        var auth = new UsernamePasswordAuthenticationToken("fantasma@exemplo.com", "senha");
        var request = new MockHttpServletRequest();
        var response = new MockHttpServletResponse();

        handler.onAuthenticationSuccess(request, response, auth);

        verifyNoInteractions(contaVinculadaService, cookie);
        assertEquals("/", response.getRedirectedUrl());
        assertTrue(response.getHeader("Set-Cookie").contains("obl_tchuros_login=1"));
    }
}
