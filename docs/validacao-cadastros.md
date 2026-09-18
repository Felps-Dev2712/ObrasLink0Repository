# Validação e padronização cadastral

CPF e CNPJ são validados pelo comprimento, caracteres permitidos e dois dígitos verificadores. São aceitos CNPJ numérico e alfanumérico, inclusive letras minúsculas na entrada, convertidas para maiúsculas. Sequências numéricas repetidas são rejeitadas. A validação é estrutural: não comprova titularidade, existência ou regularidade na Receita Federal.

Telefone e WhatsApp aceitam DDD brasileiro existente, fixo com oito dígitos começando em 2 a 5 ou celular com nove dígitos começando em 9. O código de país 55 é opcional na entrada. DDD 55 não é confundido com código do país. Não se presume um DDD nem se acrescenta um nono dígito a um número incompleto. O telefone do cliente continua opcional; WhatsApp do fornecedor é obrigatório. A validação não comprova que a linha existe ou possui WhatsApp.

As mesmas regras são aplicadas no cadastro público e ao editar cliente/fornecedor. Os documentos são comparados sem pontuação para detectar duplicidade dentro de cada tipo de perfil, excluindo o próprio registro durante uma edição. A restrição de unicidade do banco continua ativa. Um mesmo usuário pode manter os perfis de cliente e fornecedor.

Os valores válidos são persistidos e exibidos nos formatos `000.000.000-00`, `00.000.000/0000-00`, `(00) 0000-0000` e `(00) 00000-0000`. As máscaras acompanham a digitação, colagem, exclusão e edição no meio do campo. Entradas longas ou com conteúdo inválido não são truncadas silenciosamente. Campos de um perfil desmarcado no cadastro ficam desabilitados.

## Cadastros existentes

`NormalizacaoCadastrosRunner` executa depois do gerador de demonstração, na inicialização. `NormalizacaoCadastrosService.executar(true)` padroniza clientes, fornecedores (incluindo inativos) e telefones adicionais em uma transação, bloqueando alterações concorrentes durante a atualização. Nova execução com os dados já normalizados não grava novamente.

- Valores válidos recebem apenas a máscara e a normalização do código de país.
- Documentos duplicados ou inválidos e telefones inválidos são preservados, com tabela, ID, campo e motivo informados no log, sem expor os valores pessoais.
- Somente os documentos que correspondem exatamente aos e-mails, índices e valores do antigo `DataSeeder` recebem novos documentos sintéticos com verificadores corretos. Valores editados e contas reais não recebem documentos inventados.
- O gerador de demonstração passa a criar os documentos e telefones já válidos e formatados.
- A área Minha conta avisa quando o perfil possui dados estruturalmente inválidos.

Para inspecionar sem gravar, use `executar(false)`. Para desabilitar a rotina de inicialização, configure `OBRASLINK_CADASTROS_NORMALIZAR=false`. Antes de aplicá-la em outro ambiente, faça backup e confira as pendências. O banco local utilizado nesta tarefa é o contêiner `obras-link-db`.

Também existe uma execução isolada, sem iniciar o site ou o gerador de demonstração:

```sh
mvn package dependency:build-classpath -Dmdep.outputFile=target/classpath.txt
# Defina SPRING_DATASOURCE_URL, SPRING_DATASOURCE_USERNAME e SPRING_DATASOURCE_PASSWORD para o banco desejado.
sh scripts/normalizar-cadastros.sh
# Após revisar a simulação e fazer backup:
CADASTROS_APLICAR=true sh scripts/normalizar-cadastros.sh
```

O script requer JDK 21 com `jshell`, usa a mesma implementação Java e envolve a operação em uma transação. Os relatórios são gravados em `target/normalizacao-simulacao.json` e `target/normalizacao-aplicada.json`, contendo IDs e motivos, sem os valores pessoais. Verifique a saída por falhas antes de considerar a atualização aplicada.

## Testes

- Java 21: `mvn test`.
- Máscaras e validação no navegador (funções compartilhadas): `node --test src/test/js/dados-cadastrais.test.cjs`.

Referências: [cálculo do DV do CNPJ — Receita Federal](https://www.gov.br/receitafederal/pt-br/centrais-de-conteudo/publicacoes/documentos-tecnicos/cnpj/manual-dv-cnpj.pdf), [numeração — Anatel](https://www.gov.br/anatel/pt-br/regulado/numeracao/perguntas-frequentes).
