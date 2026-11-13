package com.obraslink.controller;

import com.obraslink.model.PapelUsuario;
import com.obraslink.model.Usuario;
import com.obraslink.security.ContasVinculadasCookie;
import com.obraslink.service.ContaVinculadaService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class ContaVinculadaControllerTest {
    ContaVinculadaService contaVinculadaService = mock(ContaVinculadaService.class);
    ContasVinculadasCookie cookie = mock(ContasVinculadasCookie.class);
    UserDetailsService usuarioDetailsService = mock(UserDetailsService.class);
    SecurityContextRepository securityContextRepository = mock(SecurityContextRepository.class);
    ContaVinculadaController controller = new ContaVinculadaController(contaVinculadaService, cookie, usuarioDetailsService, securityContextRepository);

    @AfterEach
    void limparContexto() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void trocaParaOUsuarioDoTokenEAtivaUmaNovaSessaoSemPedirSenha() {
        Usuario usuario = Usuario.builder().id(5L).email("bia@exemplo.com").papeis(Set.of(PapelUsuario.CLIENTE)).build();
        when(contaVinculadaService.autenticar("tok")).thenReturn(Optional.of(usuario));
        UserDetails detalhes = User.builder().username("bia@exemplo.com").password("x")
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_CLIENTE"))).build();
        when(usuarioDetailsService.loadUserByUsername("bia@exemplo.com")).thenReturn(detalhes);
        var request = new MockHttpServletRequest();
        var response = new MockHttpServletResponse();

        String destino = controller.trocar("tok", request, response, new RedirectAttributesModelMap());

        assertEquals("redirect:/", destino);
        assertEquals("bia@exemplo.com", SecurityContextHolder.getContext().getAuthentication().getName());
        verify(securityContextRepository).saveContext(any(), eq(request), eq(response));
        verify(cookie, never()).remover(any(), any(), any());
    }

    @Test
    void tokenQueNaoExisteMaisRemoveDoCookieEVoltaParaOLoginComAviso() {
        when(contaVinculadaService.autenticar("tok-ruim")).thenReturn(Optional.empty());
        var request = new MockHttpServletRequest();
        var response = new MockHttpServletResponse();
        var atributos = new RedirectAttributesModelMap();

        String destino = controller.trocar("tok-ruim", request, response, atributos);

        assertEquals("redirect:/login", destino);
        assertNull(SecurityContextHolder.getContext().getAuthentication(), "não autentica com token inválido");
        verify(cookie).remover(request, response, "tok-ruim");
        assertTrue(atributos.getFlashAttributes().containsKey("mensagemErro"));
    }

    @Test
    void removerRevogaOTokenNoBancoETiraDoCookieERedirecionaParaOndeEstava() {
        var request = new MockHttpServletRequest();
        var response = new MockHttpServletResponse();

        String destino = controller.remover("tok", "/servicos", request, response);

        assertEquals("redirect:/servicos", destino);
        verify(contaVinculadaService).revogar("tok");
        verify(cookie).remover(request, response, "tok");
    }
}
