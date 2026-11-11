package com.obraslink.service;
import com.obraslink.repository.PrestadorRepository;
import com.obraslink.repository.ServicoRepository;
import com.obraslink.repository.AvaliacaoPrestadorRepository;
import com.obraslink.repository.AvaliacaoClienteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
@Service @RequiredArgsConstructor
public class IndicadoresPlataformaService {
 private final PrestadorRepository prestadores;private final ServicoRepository servicos;private final AvaliacaoPrestadorRepository avaliacoes;private final AvaliacaoClienteRepository avaliacoesClientes;
 public record Destaque(Long id,String nome,String especialidade,long total,double media){}
 public record Depoimento(String nome,String comentario,String iniciais){}
 public record Indicadores(long profissionais,long servicos,BigDecimal satisfacao,List<Destaque> melhores,Depoimento depoimento){}
 @Transactional(readOnly=true) public Indicadores carregar(){List<Object[]> resultados=avaliacoes.satisfacaoAgregada();Object[] row=resultados==null||resultados.isEmpty()?null:resultados.get(0);long count=row==null||row[0]==null?0:((Number)row[0]).longValue();long positivos=0;if(row!=null)for(int i=1;i<row.length;i++)if(row[i]!=null)positivos+=((Number)row[i]).longValue();BigDecimal pct=count==0?null:BigDecimal.valueOf(positivos*100.0/(count*5)).setScale(1,RoundingMode.HALF_UP);
  List<Destaque> top=avaliacoes.melhoresAvaliacoes(3,PageRequest.of(0,6)).stream().map(r->new Destaque(((Number)r[0]).longValue(),(String)r[1],r[2]==null?"Serviços de construção":(String)r[2],((Number)r[3]).longValue(),((Number)r[4]).doubleValue())).toList();
  var reviews=avaliacoesClientes.depoimentosPositivos(PageRequest.of(0,1));Depoimento depoimento=null;if(!reviews.isEmpty()){Object[] r=reviews.get(0);String nome=r[0]==null||((String)r[0]).isBlank()?"Cliente ObraLink":((String)r[0]).trim();String comentario=((String)r[1]).trim();String iniciais=java.util.Arrays.stream(nome.split("\\s+")).filter(parte->!parte.isBlank()).limit(2).map(parte->parte.substring(0,1).toUpperCase(java.util.Locale.ROOT)).collect(java.util.stream.Collectors.joining());depoimento=new Depoimento(nome,comentario,iniciais.isBlank()?"C":iniciais);}
  return new Indicadores(prestadores.countByAtivoTrue(),servicos.count(),pct,top,depoimento);}
}
