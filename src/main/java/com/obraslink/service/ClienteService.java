package com.obraslink.service;

import com.obraslink.model.Cliente;
import com.obraslink.repository.ClienteRepository;
import com.obraslink.validation.DadosCadastrais;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ClienteService {
    
    private final ClienteRepository clienteRepository;
    
    public List<Cliente> findAll() {
        return clienteRepository.findAll();
    }
    
    public Optional<Cliente> findById(Long id) {
        return clienteRepository.findById(id);
    }

    public Optional<Cliente> findByUsuarioId(Long usuarioId) {
        return clienteRepository.findByUsuarioId(usuarioId);
    }
    
    @Transactional
    public Cliente save(Cliente cliente) {
        String documento = DadosCadastrais.documento(cliente.getCpfCnpj());
        String telefone = DadosCadastrais.telefone(cliente.getTelefone(), false);
        if (clienteRepository.documentoEmUso(DadosCadastrais.documentoSemMascara(documento), cliente.getId())) {
            throw new IllegalArgumentException("Este CPF/CNPJ já está cadastrado em outro perfil de cliente.");
        }
        cliente.setCpfCnpj(documento);
        cliente.setTelefone(telefone);
        return clienteRepository.save(cliente);
    }
    
    @Transactional
    public void delete(Long id) {
        clienteRepository.deleteById(id);
    }
}
