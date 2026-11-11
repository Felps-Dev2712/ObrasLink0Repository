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
import com.obraslink.validation.DadosCadastrais;
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
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private CertificacaoService certificacaoService;

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
                .nome(form.getNome().trim())
                .senha(passwordEncoder.encode(form.getSenha()))
                .papeis(papeis)
                .ativo(true)
                .build();
        usuario = usuarioRepository.save(usuario);

        if (papeis.contains(PapelUsuario.CLIENTE)) {
            Cliente cliente = Cliente.builder()
                    .nome(form.getNome().trim())
                    .email(usuario.getEmail())
                    .cpfCnpj(form.getClienteCpfCnpj().trim())
                    .telefone(form.getClienteTelefone())
                    .usuarioId(usuario.getId())
                    .endereco(enderecoCliente(form))
                    .build();
            clienteRepository.save(cliente);
        }

        if (papeis.contains(PapelUsuario.PRESTADOR)) {
            var categoriaIds = form.getCategoriasIds() != null && !form.getCategoriasIds().isEmpty()
                    ? form.getCategoriasIds().stream().filter(java.util.Objects::nonNull).distinct().toList()
                    : form.getCategoriaId() == null ? java.util.List.<Long>of()
                    : java.util.List.of(form.getCategoriaId());
            java.util.List<Categoria> categorias=new java.util.ArrayList<>();categoriaRepository.findAllById(categoriaIds).forEach(categorias::add);
            if(categorias.isEmpty()||categorias.size()!=categoriaIds.size())throw new IllegalArgumentException("Confira as categorias profissionais selecionadas.");

            Prestador prestador = Prestador.builder()
                    .nome(form.getNome().trim())
                    .email(usuario.getEmail())
                    .cpfCnpj(form.getPrestadorCpfCnpj().trim())
                    .whatsapp(form.getPrestadorWhatsapp())
                    .descricao(form.getPrestadorDescricao())
                    .categoria(categorias.get(0))
                    .categorias(categorias)
                    .usuarioId(usuario.getId())
                    .ativo(true)
                    .build();
            prestador = prestadorRepository.save(prestador);
            if (certificacaoService != null && form.getCertificacoes() != null) {
                for (var certificacao : form.getCertificacoes()) {
                    certificacaoService.registrarNoCadastro(prestador.getId(), certificacao);
                }
            }
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

        if ((cadastroCliente || cadastroPrestador) && isBlank(form.getNome())) {
            throw new IllegalArgumentException("Informe seu nome.");
        }
        if (cadastroCliente) {
            if (isBlank(form.getClienteCpfCnpj())) {
                throw new IllegalArgumentException("Para cadastro de Cliente informe CPF.");
            }
            if (clienteRepository.existsByEmail(normalizedEmail)) {
                throw new IllegalArgumentException("Ja existe cadastro de Cliente com esse e-mail.");
            }
            form.setClienteCpfCnpj(DadosCadastrais.documento(form.getClienteCpfCnpj()));
            if (form.getClienteCpfCnpj().replaceAll("\\D","").length()!=11) throw new IllegalArgumentException("O cadastro de cliente exige CPF.");
            form.setClienteTelefone(DadosCadastrais.telefone(form.getClienteTelefone(), false));
            if (clienteRepository.documentoEmUso(DadosCadastrais.documentoSemMascara(form.getClienteCpfCnpj()), null)) {
                throw new IllegalArgumentException("CPF/CNPJ de Cliente ja cadastrado.");
            }
        }

        if (cadastroPrestador) {
            if (isBlank(form.getPrestadorCpfCnpj()) || isBlank(form.getPrestadorWhatsapp())) {
                throw new IllegalArgumentException("Para cadastro de Prestador informe CNPJ e WhatsApp.");
            }
            if (form.getCategoriaId() == null && (form.getCategoriasIds()==null||form.getCategoriasIds().isEmpty())) {
                throw new IllegalArgumentException("Selecione uma categoria para o Prestador.");
            }
            if (prestadorRepository.existsByEmail(normalizedEmail)) {
                throw new IllegalArgumentException("Ja existe cadastro de Prestador com esse e-mail.");
            }
            form.setPrestadorCpfCnpj(DadosCadastrais.documento(form.getPrestadorCpfCnpj()));
            if (form.getPrestadorCpfCnpj().replaceAll("[.\\s/\\-]","").length()!=14) throw new IllegalArgumentException("O cadastro profissional exige CNPJ.");
            form.setPrestadorWhatsapp(DadosCadastrais.telefone(form.getPrestadorWhatsapp(), true));
            if (prestadorRepository.documentoEmUso(DadosCadastrais.documentoSemMascara(form.getPrestadorCpfCnpj()), null)) {
                throw new IllegalArgumentException("CPF/CNPJ de Prestador ja cadastrado.");
            }
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private com.obraslink.model.Endereco enderecoCliente(RegistroUsuarioForm f){
        if(isBlank(f.getClienteCep())||isBlank(f.getClienteLogradouro())||isBlank(f.getClienteNumero())||isBlank(f.getClienteBairro())||isBlank(f.getClienteCidade())||isBlank(f.getClienteEstado()))throw new IllegalArgumentException("Preencha o endereço completo ou deixe todos os campos em branco.");
        return com.obraslink.model.Endereco.builder().cep(f.getClienteCep().trim()).logradouro(f.getClienteLogradouro().trim()).numero(f.getClienteNumero().trim()).complemento(f.getClienteComplemento()).bairro(f.getClienteBairro().trim()).cidade(f.getClienteCidade().trim()).estado(f.getClienteEstado().trim()).build();
    }
}
