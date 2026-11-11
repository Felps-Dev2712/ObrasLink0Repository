# Revisão e melhorias ObraLink — 03/11/2026

## Regras e implementação

- O projeto é Spring Boot 3.2.2, Java 21, Thymeleaf, Spring Security, JPA e PostgreSQL. Não havia ferramenta de migração versionada; o mecanismo de schema existente é `spring.jpa.hibernate.ddl-auto=update`.
- A seleção de perfil de contas duplas fica na sessão HTTP e é validada contra os papéis persistidos. As regras principais de contato, avaliação, favoritos, certificação e disponibilidade são conferidas no servidor.
- Horários semanais são associados ao perfil autenticado; início precisa anteceder fim e intervalos no mesmo dia não podem se sobrepor. A alteração substitui a grade semanal numa transação.
- Certificações começam em `PENDENTE`. Só administrador ativo pode aprovar ou reprovar; reprovação requer observação. O envio e o estado pendente não equivalem a verificação.
- Solicitações de contato iniciadas a partir de um serviço verificam sua disponibilidade no servidor e guardam o identificador e o título do serviço no atendimento. O contato genérico do perfil continua sem vínculo a item do catálogo.
- Confirmar um atendimento exige data e horário; datas anteriores ao dia atual em `America/Sao_Paulo` são rejeitadas. A gravação do agendamento, status, timestamp e notificação ocorre na mesma transação.
- O Dashboard usa uma janela `[início do primeiro dia, início do dia seguinte ao fim]`, incluindo todo o último dia. Todos os cards, gráficos e a lista recente partem da mesma consulta filtrada.
- A satisfação exibida no login é a fração de notas individuais `>= 4` sobre as cinco dimensões de todas as avaliações, em percentual, arredondada para uma casa. Sem avaliações, o percentual fica indisponível. O destaque mostra só profissionais ativos com pelo menos três avaliações; esse limite reduz destaque por uma única nota e é uma regra editorial nova.

## Dados e configurações

O Hibernate cria/atualiza tabelas para favoritos, disponibilidade, certificações, categorias múltiplas e campos de agendamento/serviço do atendimento. Os uploads de comprovantes aceitam PDF/JPEG/PNG por assinatura do arquivo, até 10 MB, são armazenados fora do diretório público e servidos por rota que confere propriedade ou papel administrativo. O Compose persiste esses arquivos no volume `uploads_data`.

O serviço de verificação governamental de CPF/CNPJ não pôde ser ativado neste ambiente: não há credenciais, contrato ou certificado Serpro configurado. A API CPF Serpro v3 passou a exigir CPF e data de nascimento e autenticação OAuth; a API CNPJ do Serpro também exige contratação/credenciais. O catálogo Conecta para CPF/CNPJ é direcionado a órgãos públicos, portanto não é uma alternativa pública sem autorização para esta aplicação. O código ainda faz validação estrutural de CPF/CNPJ; isso **não satisfaz** a consulta oficial solicitada nem comprova situação cadastral, natureza jurídica EI ou opção pelo MEI. Não foram simuladas consultas. Antes de liberar cadastro conforme a regra MEI/EI, integrar um serviço contratado/autorizado, fornecer credenciais seguras e incluir data de nascimento se a API CPF v3 escolhida exigir.

Fontes oficiais consultadas:

- [Consulta CPF Serpro v3 — endpoint, autenticação e CPF + data de nascimento](https://centraldeajuda.serpro.gov.br/duvidas/pt/avisos/avisoconsultacpfv3/)
- [Serviço oficial de consulta de CPF — contratação e credenciais](https://www.gov.br/pt-br/servicos/obter-solucao-digital-de-consulta-de-dados-de-cadastro-de-pessoa-fisica-cpf?id=6380&origem=servico)
- [Serviço oficial de consulta de CNPJ — API Serpro](https://www.gov.br/pt-br/servicos/obter-solucao-digital-de-consulta-de-dados-do-cadastro-nacional-de-pessoas-juridicas-cnpj?id=10208&origem=servico)
- [Catálogo Conecta de consulta CNPJ e limites de acesso](https://www.gov.br/conecta/catalogo/apis/consulta-cnpj)

## Limitações que permanecem

- Foto de perfil, edição de endereços já cadastrados, múltiplos serviços durante o primeiro cadastro e grade semanal durante o cadastro ainda não foram integrados ao formulário de registro.
- A consulta oficial de CPF/CNPJ, validação do enquadramento MEI/EI e rejeição automática de empresas fora da regra dependem de serviço e credenciais autorizados ainda não configurados.
- A marca foi atualizada nas telas atuais. Os documentos legais `2026-09-09-v1` foram preservados, pois são uma versão já aceita e não devem ser alterados retroativamente. Para padronizar a marca também no texto legal, publique uma nova versão e solicite novo aceite.
- Os arquivos de certificação têm controle de acesso na aplicação, mas não criptografia própria em repouso; o volume precisa de proteção operacional e backups controlados.

## Execução dos testes

Este ambiente não tem Maven nem JDK 21 instalados, e o daemon Docker não está disponível. O Java encontrado é 8. Por isso, os testes obrigatórios e a compilação não puderam ser executados aqui. O projeto requer `mvn test` com Java 21; também é necessário validar os fluxos visuais nos quatro tamanhos de viewport solicitados.
