package com.obraslink.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "enderecos")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Endereco {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String cep;

    @Column(nullable = false)
    private String logradouro;

    @Column(nullable = false)
    private String numero;

    private String complemento;

    @Column(nullable = false)
    private String bairro;

    @Column(nullable = false)
    private String cidade;

    @Column(nullable = false)
    private String estado;

    // Coordenadas do endereço (marcadas no mapa ou obtidas pela busca de endereço). Usadas para distância e raio.
    private Double latitude;

    private Double longitude;

    public boolean temCoordenadas() {
        return latitude != null && longitude != null;
    }

    /** Copia os campos editáveis vindos de um formulário, mantendo o id deste endereço. */
    public void atualizarCom(Endereco o) {
        cep = o.cep; logradouro = o.logradouro; numero = o.numero; complemento = o.complemento;
        bairro = o.bairro; cidade = o.cidade; estado = o.estado; latitude = o.latitude; longitude = o.longitude;
    }

    /** Cópia sem id, para o prestador e o cliente da mesma conta terem endereços independentes. */
    public Endereco copia() {
        return Endereco.builder().cep(cep).logradouro(logradouro).numero(numero).complemento(complemento)
                .bairro(bairro).cidade(cidade).estado(estado).latitude(latitude).longitude(longitude).build();
    }
}
