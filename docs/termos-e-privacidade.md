# Termos de Uso e Aviso de Privacidade: aceite

Versão atual dos documentos: **2026-11-12-v2** (`TermosService.VERSAO_ATUAL`, arquivos em `templates/legal/2026-11-12-v2`).

## Aceite no cadastro (obrigatório)

O formulário de cadastro (`/register`) tem duas caixas, inicialmente desmarcadas e obrigatórias: aceite dos Termos de Uso e ciência do Aviso de Privacidade. Os dois documentos abrem em outra aba.

`AuthService.registrar` recusa o cadastro se alguma das duas não estiver marcada ("Para criar a conta, aceite os Termos de Uso e confirme a leitura do Aviso de Privacidade."). Assim nenhum dado é gravado sem o aceite. Com as duas marcadas, o aceite da versão atual é gravado em `aceites_termos` **na mesma transação** que cria o usuário: ou os dois são gravados, ou nenhum.

## Aceite de nova versão (no próximo acesso)

Depois do login, qualquer conta sem aceite da **versão atual** é direcionada a `/termos/aceitar`, incluindo administradores e contas criadas antes da versão atual. Um filtro na cadeia do Spring Security impede acesso às funcionalidades e POSTs antes do aceite. A leitura dos documentos, a saída e a exclusão da própria conta continuam disponíveis sem aceite.

`aceites_termos` registra o usuário, a versão dos documentos e a data/hora UTC do servidor (nunca valores enviados pelo navegador). Há unicidade por usuário e versão e bloqueio transacional contra cliques simultâneos. Aceites de versões anteriores continuam registrados.

## Exclusão de conta

`ContaService.excluirMinhaConta` apaga, em uma transação: aceites, favoritos, agenda, certificações, avaliações feitas e recebidas, atendimentos dos dois lados, notificações, fotos de serviços, perfis de cliente e de profissional (com serviços) e o usuário. Os arquivos (fotos, capa, foto de perfil e **comprovantes das certificações**) são apagados do disco somente depois do commit. Os textos de Termos (item 9) e Privacidade (itens 8 e 9) descrevem exatamente esse comportamento: se ele mudar, atualize os documentos em uma nova versão.

## Publicar uma nova versão

Depois que uma versão foi disponibilizada para aceite, **não altere os arquivos dela**. Para mudar o texto:

1. Crie outra pasta em `templates/legal/` (ex.: `2027-01-10-v3`) com `termos.html` e `privacidade.html`.
2. Atualize `TermosService.VERSAO_ATUAL`.
3. Todos os usuários terão de aceitar a nova versão no próximo acesso.

## Antes de publicar em produção (revisão jurídica)

Os textos são uma proposta técnica alinhada ao funcionamento da plataforma, **não um parecer jurídico**. Antes de publicar, o responsável deve validar com o jurídico:

- identificação do controlador (razão social/CNPJ) e o canal efetivo de privacidade/encarregado (o e-mail `contato@obraslink.com.br` foi reaproveitado do rodapé e não foi verificado);
- as bases legais por finalidade;
- a guarda dos **registros de acesso por 6 meses** (Marco Civil, art. 15): o texto informa essa retenção, então a infraestrutura precisa de fato guardar IP, data e hora das requisições por esse prazo, inclusive após a exclusão da conta;
- o ciclo real de cópias de segurança (backups) e os fornecedores de infraestrutura/hospedagem.

Referência: [Perguntas frequentes da ANPD](https://www.gov.br/anpd/pt-br/acesso-a-informacao/perguntas-frequentes/perguntas-frequentes).

## Verificação

- Testes: `mvn test` com Java 21.
- Cadastro sem marcar as caixas: o formulário não envia; enviado por fora da tela, o servidor recusa e nada é gravado.
- Cadastro com as caixas marcadas: a conta entra direto, sem pedir o aceite de novo, e `aceites_termos` tem a versão atual.
- Conta com aceite de versão anterior: ao entrar, é direcionada a `/termos/aceitar`.
- Exclusão de conta de profissional com certificação: o arquivo do comprovante some da pasta de uploads.
