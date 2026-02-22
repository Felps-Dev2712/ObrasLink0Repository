package com.obraslink.config;

import com.obraslink.model.Categoria;
import com.obraslink.repository.CategoriaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;
import java.util.List;

@Configuration
@RequiredArgsConstructor
public class DataSeeder {

    private final CategoriaRepository categoriaRepository;

    @Bean
    public CommandLineRunner initData() {
        return args -> {
            if (categoriaRepository.count() == 0) {
                List<Categoria> categorias = Arrays.asList(
                        Categoria.builder().nome("Pedreiro").descricao("Construção de paredes, muros, pisos e acabamentos.").icone("🧱").build(),
                        Categoria.builder().nome("Mestre de Obras").descricao("Coordenação e supervisão completa da obra.").icone("👷").build(),
                        Categoria.builder().nome("Empreiteira").descricao("Empresas especializadas para projetos completos.").icone("🏢").build(),
                        Categoria.builder().nome("Eletricista").descricao("Instalações elétricas residenciais e comerciais.").icone("⚡").build(),
                        Categoria.builder().nome("Encanador").descricao("Sistemas hidráulicos, manutenção e instalações.").icone("🔧").build(),
                        Categoria.builder().nome("Pintor").descricao("Pintura interna e externa com excelente acabamento.").icone("🎨").build()
                );
                categoriaRepository.saveAll(categorias);
                System.out.println("Categorias seed data initialized.");
            }
        };
    }
}
