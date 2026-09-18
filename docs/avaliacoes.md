# Avaliação de serviços concluídos

Uma avaliação nova exige um atendimento `CONCLUIDO` que pertença ao usuário autenticado e ao prestador da página. O servidor verifica também a conta responsável pelo prestador, o perfil de cliente e a proibição de avaliar o próprio perfil. IDs enviados pelo navegador não bastam para autorizar a operação.

O formulário lista apenas os atendimentos concluídos ainda não avaliados pelo cliente. Cada atendimento admite uma avaliação, protegida por bloqueio transacional e unicidade de `avaliacoes_prestadores.atendimento_id` no banco. Uma nova contratação concluída permite outra avaliação.

O vínculo é com a contratação registrada no fluxo do WhatsApp, pois esse fluxo não identifica um item do catálogo. A avaliação não exige que o fornecedor mantenha um serviço no catálogo para permitir ao cliente avaliar o atendimento concluído.

Qualidade, prazo, preço, organização e atendimento têm notas independentes de 1 a 5. Todas são obrigatórias, começam desmarcadas e podem ser selecionadas com mouse ou teclado. O comentário é opcional. Clicar nas estrelas não envia o formulário; o usuário deve clicar em **Enviar avaliação**. Em caso de erro, as seleções e o comentário são mantidos.

Na tela de notificações, atendimentos concluídos do cliente têm um link para avaliar o prestador. A página verifica novamente a elegibilidade e não mostra o formulário quando não há atendimentos disponíveis.

## Banco e dados anteriores

Na configuração atual, `spring.jpa.hibernate.ddl-auto=update` cria a coluna `atendimento_id` e sua restrição de unicidade ao iniciar a nova versão. A coluna permite nulos exclusivamente para preservar avaliações antigas; novas avaliações passam obrigatoriamente pela validação do atendimento no serviço. Não são inventados vínculos de contratação para avaliações antigas.

## Validação

- `mvn test` com Java 21: permissões, transições, duplicidade, notas obrigatórias e renderização das páginas.
- `node --test src/test/js/avaliacoes.test.cjs`: independência dos critérios e ausência de envio automático.
# Avaliação do cliente pelo prestador

Depois de concluir o atendimento, o prestador pode abrir **Avaliar cliente / ver avaliação** em Notificações. O formulário exige notas de 1 a 5 para comunicação, pontualidade e disponibilidade, e cumprimento do combinado. O comentário é opcional (até 2000 caracteres). As estrelas apenas selecionam as notas; o botão **Enviar avaliação** confirma o envio.

O servidor verifica a conta ativa, o papel de prestador, a propriedade atual do perfil, os participantes e a conclusão do atendimento. Os IDs das contas vêm do atendimento registrado. Cada atendimento permite uma avaliação do cliente, independentemente da avaliação do prestador feita pelo cliente. O bloqueio transacional do atendimento e a restrição única no banco impedem duplicatas.

O cliente recebe uma notificação com acesso à avaliação. Somente os dois participantes podem consultar a página `/atendimentos/{id}/avaliacao-cliente`; avaliações recebidas são somente leitura. Excluir a conta de um participante também remove essas avaliações.
