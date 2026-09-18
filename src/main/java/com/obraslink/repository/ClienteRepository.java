package com.obraslink.repository;

import com.obraslink.model.Cliente;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

@Repository
public interface ClienteRepository extends JpaRepository<Cliente, Long> {
    @Query(value = "SELECT count(*) > 0 FROM clientes WHERE regexp_replace(upper(cpf_cnpj), '[.[:space:]/-]', '', 'g') = :documento AND id <> coalesce(:id, -1)", nativeQuery = true)
    boolean documentoEmUso(@Param("documento") String documento, @Param("id") Long id);
    Optional<Cliente> findByUsuarioId(Long usuarioId);

    boolean existsByIdAndUsuarioId(Long id, Long usuarioId);

    boolean existsByEmail(String email);

    boolean existsByCpfCnpj(String cpfCnpj);
}
