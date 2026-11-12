package com.obraslink.security;

import com.obraslink.model.Categoria;
import com.obraslink.model.Endereco;
import com.obraslink.model.PapelUsuario;
import com.obraslink.model.Prestador;
import com.obraslink.model.Usuario;
import com.obraslink.service.AcessoService;
import com.obraslink.service.PrestadorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Edição do perfil profissional pelo próprio prestador (POST /prestadores/minha-conta). */
@SpringJUnitConfig({TermosSecurityTest.Config.class, AvaliacaoTelaTest.Config.class})
@WebAppConfiguration
class PerfilProfissionalTelaTest {
    @Autowired WebApplicationContext context;
    @Autowired AcessoService acesso;
    @Autowired PrestadorService prestadores;
    private MockMvc mvc;
    private Prestador prestador;

    @BeforeEach
    void preparar() {
        reset(prestadores, acesso);
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
        when(acesso.getUsuarioAtualOrThrow()).thenReturn(Usuario.builder().id(40L).ativo(true).papeis(Set.of(PapelUsuario.PRESTADOR)).build());
        prestador = Prestador.builder().id(30L).usuarioId(40L).nome("Antigo").anosExperiencia(12).precoMinimo(new BigDecimal("150.00"))
                .categoria(Categoria.builder().id(1L).nome("Eletricista").build())
                .endereco(Endereco.builder().cep("05422-001").logradouro("Rua dos Pinheiros").numero("500").bairro("Pinheiros")
                        .cidade("São Paulo").estado("SP").latitude(-23.566).longitude(-46.683).build()).build();
        when(prestadores.findByUsuarioId(40L)).thenReturn(Optional.of(prestador));
    }

    @Test
    void anosDeExperienciaEPrecoMinimoNaoSaoMaisEditaveisNemApagados() throws Exception {
        mvc.perform(post("/prestadores/minha-conta")
                        .param("nome", "Camille Profissional").param("cpfCnpj", "11.222.333/0001-81").param("whatsapp", "(41) 91234-5678")
                        .param("descricao", "Nova descrição").param("raioAtendimentoKm", "10")
                        .param("anosExperiencia", "99").param("precoMinimo", "1.00") // enviados por fora da tela: devem ser ignorados
                        .param("endereco.cep", "80215-100").param("endereco.logradouro", "Rua Engenheiros Rebouças").param("endereco.numero", "1206")
                        .param("endereco.bairro", "Rebouças").param("endereco.cidade", "Curitiba").param("endereco.estado", "PR")
                        .param("endereco.latitude", "-25.44").param("endereco.longitude", "-49.26"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/clientes"))
                .andExpect(flash().attribute("mensagem", "Dados profissionais atualizados com sucesso!"));

        var salvo = ArgumentCaptor.forClass(Prestador.class);
        verify(prestadores).save(salvo.capture());
        assertEquals("Camille Profissional", salvo.getValue().getNome());
        assertEquals("Nova descrição", salvo.getValue().getDescricao());
        assertEquals(12, salvo.getValue().getAnosExperiencia(), "valor atual preservado");
        assertEquals(new BigDecimal("150.00"), salvo.getValue().getPrecoMinimo(), "valor atual preservado");
    }
}
