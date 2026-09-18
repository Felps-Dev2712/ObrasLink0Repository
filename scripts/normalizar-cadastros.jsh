import com.obraslink.service.NormalizacaoCadastrosService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import java.nio.file.*;

// Executar com Java 21, target/classes e as dependências do projeto no classpath.
// Sem CADASTROS_APLICAR=true apenas simula; não inicia web, seeder ou outros runners.
{
    try {
        var ds = new DriverManagerDataSource(System.getenv("SPRING_DATASOURCE_URL"), System.getenv("SPRING_DATASOURCE_USERNAME"), System.getenv("SPRING_DATASOURCE_PASSWORD"));
        var jdbc = new JdbcTemplate(ds);
        var service = new NormalizacaoCadastrosService(jdbc);
        var tx = new TransactionTemplate(new DataSourceTransactionManager(ds));
        boolean aplicar = Boolean.parseBoolean(System.getenv("CADASTROS_APLICAR"));
        var resultado = tx.execute(status -> service.executar(aplicar));
        var json = new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(resultado);
        Files.writeString(Path.of("target", aplicar ? "normalizacao-aplicada.json" : "normalizacao-simulacao.json"), json);
        System.out.println(json);
    } catch (Exception ex) {
        ex.printStackTrace();
        System.exit(1);
    }
}
/exit
