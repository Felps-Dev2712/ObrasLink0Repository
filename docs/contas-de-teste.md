# Contas de teste (ambiente local)

Dados **fictícios** criados pelo `DataSeeder` quando o banco está vazio. Nomes, CPFs, CNPJs, telefones e endereços são inventados (os endereços usam ruas reais de São Paulo e região só para o mapa ficar realista). Não use essas senhas fora do ambiente local.

## Contas principais

| Perfil | E-mail | Senha |
|---|---|---|
| Cliente | `cliente.teste@obraslink.com` | `Obras@2026` |
| Prestador (eletricista) | `prestador.teste@obraslink.com` | `Obras@2026` |
| Cliente + prestador (pintor) | `ambos.teste@obraslink.com` | `Obras@2026` |
| Administrador | `admin@obraslink.com` | `admin123` |

Todas as outras contas (`*.cliente@obraslink.com` e os demais profissionais) também usam a senha `Obras@2026`.

## O que dá para testar com cada uma

**cliente.teste** (Camila, Vila Mariana)
- Lista de profissionais ordenada por distância, filtro "Atendem minha região" e mapa.
- 3 favoritos já marcados.
- Atendimentos com o prestador de teste em todos os status: contato solicitado, em andamento (agendado), concluído avaliado e concluído **sem avaliação** (para testar a avaliação).
- Atendimento concluído com a Ana Costa Hidráulica sem avaliação.
- Notificações lidas e não lidas; troca da foto de perfil e do endereço em Minha conta.

**prestador.teste** (Rafael, Pinheiros, raio de 20 km)
- Painel com histórico de cerca de 9 meses: atendimentos, avaliações recebidas e nota média.
- Pedidos de contato em aberto para responder e atendimentos em andamento.
- Um atendimento concluído sem avaliação do cliente (para avaliar o cliente).
- Certificações aprovada, pendente e reprovada (com observação).
- Disponibilidade semanal, 4 serviços (1 indisponível), mapa da área de atendimento no perfil público.

**ambos.teste** (Lucas, Moema)
- Troca entre o perfil de cliente e o de prestador.

**admin**
- Validação das certificações pendentes e cadastros de clientes e profissionais.

## Para testar casos específicos

- **Fora da região:** "Campinas Obras e Projetos" fica em Campinas e não atende São Paulo. Use-o para ver o filtro de região e a distância.
- **Aceite dos termos:** `igor.cliente@obraslink.com` ainda não aceitou os termos; ao entrar, a tela de aceite aparece.

## Recriar os dados

O seeder só roda com a tabela de usuários vazia. Para começar do zero no Docker local:

```bash
docker compose stop app
docker compose exec db psql -U obraslink -d obraslink -c "DROP SCHEMA public CASCADE; CREATE SCHEMA public;"
docker compose run --rm --no-deps --entrypoint sh app -c "rm -rf /app/uploads/*"
docker compose up -d --build app
```

Confira o usuário e o nome do banco no `docker-compose.yml` antes de rodar. Isso **apaga todos os dados locais**.
