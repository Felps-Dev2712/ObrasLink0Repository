package com.obraslink.service;

import com.obraslink.model.Cliente;
import com.obraslink.repository.ClienteRepository;
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
    
    @Transactional
    public Cliente save(Cliente cliente) {
        return clienteRepository.save(cliente);
    }
    
    @Transactional
    public void delete(Long id) {
        clienteRepository.deleteById(id);
    }
}
