# Contato e acompanhamento do serviço

O botão de WhatsApp envia um POST com proteção CSRF. O servidor identifica o cliente pela sessão autenticada e consulta o fornecedor pelo ID da URL. O usuário do fornecedor é obtido do cadastro, nunca de um campo enviado pelo navegador.

O contato e a notificação são persistidos na mesma transação antes do redirecionamento ao WhatsApp. Cliques repetidos reutilizam o atendimento aberto entre o mesmo cliente e fornecedor; após a conclusão, um novo contato cria outro atendimento. Um bloqueio no banco serializa cliques concorrentes. Fornecedores sem conta vinculada, inativos ou com telefone inválido não recebem solicitações.

As etapas são: contato solicitado, serviço em andamento e serviço concluído. Somente o usuário do fornecedor responsável pode confirmar a contratação e depois concluir o serviço. Cada transição notifica o cliente. O andamento representa etapas, sem estimar percentual de execução. A plataforma não lê a conversa nem confirma o envio da mensagem no WhatsApp.

A navegação consulta as notificações não lidas a cada 15 segundos enquanto a página está visível. Na aba de notificações, novas mensagens recarregam a página para atualizar os atendimentos. As notificações continuam salvas se o destinatário estiver offline.

Com a configuração atual `spring.jpa.hibernate.ddl-auto=update`, a tabela `atendimentos` e seus índices são criados na inicialização. A exclusão de conta também remove seus atendimentos.

## Validação manual

1. Entre como cliente e clique em Contato pelo WhatsApp no perfil de um fornecedor com conta e telefone válidos.
2. Em outro navegador, entre como esse fornecedor e abra Notificações. Confira o nome do cliente e o botão de confirmação.
3. Repita o contato antes da conclusão e confira que não surgiu um segundo atendimento.
4. Confirme a contratação como fornecedor e confira a etapa em andamento nas notificações do cliente.
5. Conclua o serviço como fornecedor e confira a conclusão e sua data para o cliente.
6. Verifique que outro usuário não consegue confirmar ou concluir esse atendimento.

Testes automatizados: `mvn test`, com JDK 21 e Maven.
