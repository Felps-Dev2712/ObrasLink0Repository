package com.obraslink.service;

import com.obraslink.dto.RegistroUsuarioForm;
import com.obraslink.model.Categoria;
import com.obraslink.model.Cliente;
import com.obraslink.model.PapelUsuario;
import com.obraslink.model.Prestador;
import com.obraslink.model.Usuario;
import com.obraslink.repository.CategoriaRepository;
import com.obraslink.repository.ClienteRepository;
import com.obraslink.repository.PrestadorRepository;
import com.obraslink.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UsuarioRepository usuarioRepository;
    private final ClienteRepository clienteRepository;
    private final PrestadorRepository prestadorRepository;
    private final CategoriaRepository categoriaRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public void registrar(RegistroUsuarioForm form) {
        validarFormulario(form);

        Set<PapelUsuario> papeis = new HashSet<>();
        if (Boolean.TRUE.equals(form.getCadastroCliente())) {
            papeis.add(PapelUsuario.CLIENTE);
        }
        if (Boolean.TRUE.equals(form.getCadastroPrestador())) {
            papeis.add(PapelUsuario.PRESTADOR);
        }

        Usuario usuario = Usuario.builder()
                .email(form.getEmail().trim().toLowerCase())
                .senha(passwordEncoder.encode(form.getSenha()))
                .papeis(papeis)
                .ativo(true)
                .build();
        usuario = usuarioRepository.save(usuario);

        if (papeis.contains(PapelUsuario.CLIENTE)) {
            Cliente cliente = Cliente.builder()
                    .nome(form.getClienteNome().trim())
                    .email(usuario.getEmail())
                    .cpfCnpj(form.getClienteCpfCnpj().trim())
                    .telefone(form.getClienteTelefone())
                    .usuarioId(usuario.getId())
                    .build();
            clienteRepository.save(cliente);
        }

        if (papeis.contains(PapelUsuario.PRESTADOR)) {
            Categoria categoria = categoriaRepository.findById(form.getCategoriaId())
                    .orElseThrow(() -> new IllegalArgumentException("Categoria informada nao existe."));

            Prestador prestador = Prestador.builder()
                    .nome(form.getPrestadorNome().trim())
                    .email(usuario.getEmail())
                    .cpfCnpj(form.getPrestadorCpfCnpj().trim())
                    .descricao(form.getPrestadorDescricao())
                    .categoria(categoria)
                    .usuarioId(usuario.getId())
                    .ativo(true)
                    .build();
            prestadorRepository.save(prestador);
        }
    }

    private void validarFormulario(RegistroUsuarioForm form) {
        if (form.getEmail() == null || form.getEmail().isBlank()) {
            throw new IllegalArgumentException("Informe um e-mail valido.");
        }
        if (form.getSenha() == null || form.getSenha().isBlank()) {
            throw new IllegalArgumentException("Informe uma senha.");
        }
        if (!form.getSenha().equals(form.getConfirmarSenha())) {
            throw new IllegalArgumentException("As senhas nao conferem.");
        }

        String normalizedEmail = form.getEmail().trim().toLowerCase();
        if (usuarioRepository.existsByEmail(normalizedEmail)) {
            throw new IllegalArgumentException("Ja existe uma conta com esse e-mail.");
        }

        boolean cadastroCliente = Boolean.TRUE.equals(form.getCadastroCliente());
        boolean cadastroPrestador = Boolean.TRUE.equals(form.getCadastroPrestador());

        if (!cadastroCliente && !cadastroPrestador) {
            throw new IllegalArgumentException("Selecione pelo menos um perfil: Cliente ou Prestador.");
        }

        if (cadastroCliente) {
            if (isBlank(form.getClienteNome()) || isBlank(form.getClienteCpfCnpj())) {
                throw new IllegalArgumentException("Para cadastro de Cliente informe nome e CPF/CNPJ.");
            }
            if (clienteRepository.existsByEmail(normalizedEmail)) {
                throw new IllegalArgumentException("Ja existe cadastro de Cliente com esse e-mail.");
            }
            if (clienteRepository.existsByCpfCnpj(form.getClienteCpfCnpj().trim())) {
                throw new IllegalArgumentException("CPF/CNPJ de Cliente ja cadastrado.");
            }
        }

        if (cadastroPrestador) {
            if (isBlank(form.getPrestadorNome()) || isBlank(form.getPrestadorCpfCnpj())) {
                throw new IllegalArgumentException("Para cadastro de Prestador informe nome e CPF/CNPJ.");
            }
            if (form.getCategoriaId() == null) {
                throw new IllegalArgumentException("Selecione uma categoria para o Prestador.");
            }
            if (prestadorRepository.existsByEmail(normalizedEmail)) {
                throw new IllegalArgumentException("Ja existe cadastro de Prestador com esse e-mail.");
            }
            if (prestadorRepository.existsByCpfCnpj(form.getPrestadorCpfCnpj().trim())) {
                throw new IllegalArgumentException("CPF/CNPJ de Prestador ja cadastrado.");
            }
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
