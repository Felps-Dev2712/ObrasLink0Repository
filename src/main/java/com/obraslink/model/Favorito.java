package com.obraslink.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity @Table(name="favoritos", uniqueConstraints=@UniqueConstraint(name="uk_favorito_cliente_prestador", columnNames={"cliente_id","prestador_id"}))
@Getter @NoArgsConstructor
public class Favorito {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(name="cliente_id", nullable=false) private Long clienteId;
    @Column(name="prestador_id", nullable=false) private Long prestadorId;
    @Column(nullable=false, updatable=false) private LocalDateTime criadoEm=LocalDateTime.now();
    public Favorito(Long clienteId, Long prestadorId) { this.clienteId=clienteId; this.prestadorId=prestadorId; }
}
