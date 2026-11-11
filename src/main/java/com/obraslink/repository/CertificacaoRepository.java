package com.obraslink.repository;
import com.obraslink.model.Certificacao;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
public interface CertificacaoRepository extends JpaRepository<Certificacao,Long> {
 List<Certificacao> findByPrestadorIdOrderByCriadoEmDesc(Long prestadorId);
 Optional<Certificacao> findByIdAndPrestadorId(Long id,Long prestadorId);
 void deleteByPrestadorId(Long prestadorId);
 List<Certificacao> findByEstadoOrderByCriadoEmAsc(Certificacao.Estado estado);
}
