package com.obraslink.security;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import com.obraslink.service.AcessoService;
import com.obraslink.service.TermosService;

@Configuration
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {
    private final AcessoService acessoService;
    private final TermosService termosService;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        // Só login, cadastro, recuperação de senha, documentos legais e arquivos estáticos são públicos.
                        // Todo o resto (inclusive a home, categorias, profissionais e vitrine) exige login.
                        .requestMatchers("/login", "/register", "/recuperar-senha", "/termos", "/privacidade", "/error", "/css/**", "/js/**").permitAll()
                        .anyRequest().authenticated()
                )
                .exceptionHandling(exception -> exception
                        // Visitante na home vai direto ao login; nas demais telas o login mostra o aviso de acesso restrito.
                        .authenticationEntryPoint((request, response, authException) -> {
                            String path = request.getRequestURI().substring(request.getContextPath().length());
                            boolean home = path.isEmpty() || "/".equals(path);
                            response.sendRedirect(request.getContextPath() + (home ? "/login" : "/login?required"));
                        })
                )
                .formLogin(form -> form
                        .loginPage("/login")
                        .defaultSuccessUrl("/", true)
                        .permitAll()
                )
                .logout(logout -> logout
                        .logoutSuccessUrl("/login?logout")
                        .permitAll()
                );

        http.addFilterAfter(new TermosAceiteFilter(acessoService, termosService), AuthorizationFilter.class);
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
