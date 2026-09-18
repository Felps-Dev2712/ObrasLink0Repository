# Aceite no primeiro acesso

Depois do login, qualquer conta sem aceite da versão atual é direcionada a `/termos/aceitar`, incluindo contas já existentes e administradores. Um filtro na cadeia do Spring Security impede acesso direto às funcionalidades e POSTs antes do aceite. A autenticação e a proteção CSRF continuam ativas.

O formulário exige duas marcações inicialmente desmarcadas: aceitação dos Termos de Uso e ciência do Aviso de Privacidade. Não se trata de consentimento para marketing ou tratamento opcional. A leitura dos documentos, a saída e a exclusão da própria conta permanecem disponíveis sem aceite.

`aceites_termos` registra o usuário autenticado, a versão conjunta dos documentos e a data/hora UTC do servidor. Há unicidade por usuário e versão e bloqueio transacional para evitar duplicação por cliques simultâneos. Os dados não vêm de um ID ou data enviados pelo navegador. A exclusão de conta remove seus registros de aceite, conforme a política atual do projeto.

## Documentos e publicação

Os textos em `templates/legal/2026-09-09-v1` são textos iniciais para revisão, não uma avaliação de conformidade com a LGPD. O contato foi reutilizado do rodapé existente (`contato@obraslink.com.br`); sua validade não foi verificada. Antes de publicar, o responsável deve validar os textos e informar sua identificação/razão social, o canal efetivo de privacidade, as bases legais por finalidade, os fornecedores de infraestrutura e os prazos/procedimentos reais de retenção, backups e exercício de direitos.

O cadastro já coleta dados antes do primeiro login. Por isso, há links para os documentos também no cadastro. O aceite posterior não substitui a necessidade de determinar bases legais e prestar informações antes da coleta.

Referência: [Perguntas frequentes da ANPD](https://www.gov.br/anpd/pt-br/acesso-a-informacao/perguntas-frequentes/perguntas-frequentes).

Depois de disponibilizar uma versão para aceite, preserve seus arquivos. Para publicar alterações, crie outra pasta com os dois documentos e atualize `TermosService.VERSAO_ATUAL`. Uma tela aberta com versão antiga será rejeitada e pedirá a leitura da versão atual. Aceites anteriores permanecem registrados.

Com `spring.jpa.hibernate.ddl-auto=update`, a tabela é criada ao iniciar a aplicação. Não são atribuídos aceites automáticos às contas existentes.

## Verificação

- Testes: `mvn test` com Java 21.
- Entre com uma conta sem aceite: o início e as URLs de funcionalidades devem direcionar à confirmação.
- Confira os dois links, a opção de sair e a validação das caixas desmarcadas.
- Aceite e faça login novamente: a mesma versão não deve ser solicitada outra vez.
- Confira o registro no banco e o comportamento com uma nova versão.
