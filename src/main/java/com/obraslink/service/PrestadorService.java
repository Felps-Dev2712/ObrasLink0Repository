package com.obraslink.service;

import com.obraslink.model.Prestador;
import com.obraslink.repository.PrestadorRepository;
import com.obraslink.validation.DadosCadastrais;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PrestadorService {
    
    private final PrestadorRepository prestadorRepository;
    
    public List<Prestador> findAllActive() {
        return prestadorRepository.findAtivosParaListagem();
    }
    
    public List<Prestador> findByCategoria(Long categoriaId) {
        return prestadorRepository.findByCategoriaIdAndAtivoTrue(categoriaId);
    }
    
    public List<Prestador> search(String termo) {
        if (termo == null || termo.trim().isEmpty()) {
            return findAllActive();
        }
        return prestadorRepository.searchByNome(termo);
    }
    
    public Optional<Prestador> findById(Long id) {
        return prestadorRepository.findById(id);
    }
    public Optional<Prestador> findPerfilPublico(Long id){return prestadorRepository.findPerfilPublico(id);}

    public Optional<Prestador> findByUsuarioId(Long usuarioId) {
        return prestadorRepository.findByUsuarioId(usuarioId);
    }
    
    @Transactional
    public Prestador save(Prestador prestador) {
        String documento = DadosCadastrais.documento(prestador.getCpfCnpj());
        String whatsapp = DadosCadastrais.telefone(prestador.getWhatsapp(), true);
        if (prestadorRepository.documentoEmUso(DadosCadastrais.documentoSemMascara(documento), prestador.getId())) {
            throw new IllegalArgumentException("Este CPF/CNPJ já está cadastrado em outro perfil de fornecedor.");
        }
        if (prestador.getTelefones() != null) {
            prestador.getTelefones().forEach(t -> DadosCadastrais.telefone(t.getNumero(), true));
        }
        prestador.setCpfCnpj(documento);
        prestador.setWhatsapp(whatsapp);
        if(prestador.getCategorias()==null)prestador.setCategorias(new java.util.ArrayList<>());
        if(prestador.getCategorias().isEmpty()&&prestador.getCategoria()!=null)prestador.setCategorias(new java.util.ArrayList<>(List.of(prestador.getCategoria())));
        else if(prestador.getCategoria()!=null&&prestador.getCategorias().stream().noneMatch(c->java.util.Objects.equals(c.getId(),prestador.getCategoria().getId())))prestador.getCategorias().add(prestador.getCategoria());
        if (prestador.getTelefones() != null) {
            prestador.getTelefones().forEach(t -> {
                t.setNumero(DadosCadastrais.telefone(t.getNumero(), true));
                t.setPrestador(prestador);
            });
        }
        return prestadorRepository.save(prestador);
    }
    
    @Transactional
    public void delete(Long id) {
        prestadorRepository.findById(id).ifPresent(p -> {
            p.setAtivo(false);
            prestadorRepository.save(p);
        });
    }
}
