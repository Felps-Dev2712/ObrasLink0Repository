package com.obraslink.repository;
import com.obraslink.model.Favorito;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.obraslink.model.Prestador;
public interface FavoritoRepository extends JpaRepository<Favorito,Long> {
 List<Favorito> findByClienteIdOrderByCriadoEmDesc(Long clienteId);
 Optional<Favorito> findByClienteIdAndPrestadorId(Long clienteId,Long prestadorId);
 boolean existsByClienteIdAndPrestadorId(Long clienteId,Long prestadorId);
 void deleteByClienteIdAndPrestadorId(Long clienteId,Long prestadorId);
 void deleteByClienteId(Long clienteId);
 void deleteByPrestadorId(Long prestadorId);
 @Query("select p from Favorito f join Prestador p on p.id=f.prestadorId where f.clienteId=:clienteId and p.ativo=true and exists (select u.id from Usuario u where u.id=p.usuarioId and u.ativo=true) order by f.criadoEm desc")
 List<Prestador> findPrestadoresFavoritos(@Param("clienteId") Long clienteId);
}
