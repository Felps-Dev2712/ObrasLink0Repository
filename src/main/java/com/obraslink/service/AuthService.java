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
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.obraslink.repository.AceiteTermosRepository aceiteTermosRepository;

    /** Endereço da conta a partir do formulário de cadastro (cliente e prestador usam o mesmo). */
    public static com.obraslink.model.Endereco enderecoDoFormulario(RegistroUsuarioForm f) {
        return com.obraslink.model.Endereco.builder()
                .cep(f.getClienteCep()).logradouro(f.getClienteLogradouro()).numero(f.getClienteNumero())
                .complemento(f.getClienteComplemento()).bairro(f.getClienteBairro()).cidade(f.getClienteCidade())
                .estado(f.getClienteEstado()).latitude(f.getClienteLatitude()).longitude(f.getClienteLongitude()).build();
    }

    @Transactional
    public void registrar(RegistroUsuarioForm form) {
        validarFormulario(form);
        // Endereço obrigatório para qualquer perfil: é ele que permite buscar prestadores por proximidade.
        com.obraslink.model.Endereco endereco = enderecoDoFormulario(form);
        LocalizacaoService.validarEndereco(endereco);
        int raio = LocalizacaoService.validarRaio(form.getPrestadorRaioKm());
        // Sem o aceite dos Termos e a ciência do Aviso de Privacidade a conta não é criada.
        if (!Boolean.TRUE.equals(form.getAceitouTermos()) || !Boolean.TRUE.equals(form.getCientePrivacidade())) {
            throw new IllegalArgumentException("Para criar a conta, aceite os Termos de Uso e confirme a leitura do Aviso de Privacidade.");
        }

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
        // Registra o aceite junto com a conta (mesma transação): versão atual dos documentos e data/hora do servidor.
        if (aceiteTermosRepository != null) {
            aceiteTermosRepository.save(new com.obraslink.model.AceiteTermos(usuario.getId(), TermosService.VERSAO_ATUAL));
        }

        if (papeis.contains(PapelUsuario.CLIENTE)) {
            Cliente cliente = Cliente.builder()
                    .nome(form.getNome().trim())
                    .email(usuario.getEmail())
                    .cpfCnpj(form.getClienteCpfCnpj().trim())
                    .telefone(form.getClienteTelefone())
                    .usuarioId(usuario.getId())
                    .endereco(endereco)
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
                    // Conta com os dois perfis: cada perfil tem sua cópia do endereço (podem ser editados separadamente)
                    .endereco(papeis.contains(PapelUsuario.CLIENTE) ? endereco.copia() : endereco)
                    .raioAtendimentoKm(raio)
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
        if (form.getSenha().length() < 8) {
            throw new IllegalArgumentException("A senha deve ter pelo menos 8 caracteres.");
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

}
