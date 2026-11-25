# Contas da instância pública de demonstração

Dados criados pelo `DemoSeeder` (perfil Spring `demo`), usados na instância pública hospedada fora do
ambiente de desenvolvimento. Nomes, CPFs, CNPJs, telefones e endereços são fictícios (sem "Teste" no
nome, para parecer uso real), mas os endereços usam ruas reais de São Paulo e região só para o mapa
ficar realista. **Não use essas senhas em contas reais.**

Para o ambiente de desenvolvimento local (`DataSeeder`, perfil padrão), veja [contas-de-teste.md](contas-de-teste.md).

## Contas principais

| Perfil | E-mail | Senha |
|---|---|---|
| Cliente | `camila.andrade@obraslink.com` | `Obras@2026` |
| Prestador (eletricista + pintor) | `rafael.silva@obraslink.com` | `Obras@2026` |
| Cliente + prestador (pintor) | `lucas.ferreira@obraslink.com` | `Obras@2026` |
| Administrador | `admin@obraslink.com` | `admin123` |

Todas as outras contas (os demais clientes e profissionais) também usam a senha `Obras@2026`.

## O que dá para testar com cada uma

**camila.andrade** (cliente, Vila Mariana)
- Lista de profissionais ordenada por distância, filtro "Atendem minha região" e mapa.
- 3 favoritos já marcados.
- Atendimentos com o prestador principal em todos os status: contato solicitado, em andamento (agendado),
  concluído avaliado e concluído **sem avaliação** (para testar a avaliação).
- Pedido de orçamento a vários profissionais de uma vez, inclusive escolhendo entre as duas
  especialidades do Rafael Silva Elétrica (Eletricista e Pintor).
- Notificações lidas e não lidas; troca da foto de perfil e do endereço em Minha conta.

**rafael.silva** (prestador, Pinheiros, raio de 20 km, Eletricista **e** Pintor)
- Painel com histórico de cerca de 9 meses: atendimentos, avaliações recebidas e nota média.
- Pedidos de contato em aberto para responder e atendimentos em andamento.
- Certificações aprovada, pendente e reprovada (com observação).
- Disponibilidade semanal, 4 serviços (1 indisponível), mapa da área de atendimento no perfil público.
- Tem duas especialidades cadastradas: útil para testar a escolha de especialidade no pedido de
  orçamento, tanto sozinho quanto junto com outro profissional da mesma categoria.

**lucas.ferreira** (cliente + prestador, Moema)
- Troca entre o perfil de cliente e o de prestador pelo mesmo login.

**admin**
- Validação das certificações pendentes e cadastros de clientes e profissionais.
- Revisão da denúncia pendente (ver "Casos específicos" abaixo).

## Casos específicos

- **Fora da região:** "Campinas Obras e Projetos" fica em Campinas e não atende São Paulo. Use-o para ver
  o filtro de região e a distância.
- **Denúncia pendente:** há uma denúncia aguardando análise, visível em `/admin/denuncias`.
- **Termos de Uso desatualizados:** a conta `igor.nascimento@obraslink.com` ainda não aceitou a versão
  atual dos documentos — serve para testar a tela de aceite obrigatório no próximo login.

## Recriar os dados

O `DemoSeeder` só roda quando o banco está **vazio** (nenhum usuário cadastrado). Para recriar os dados
do zero na instância de demonstração:

```bash
docker compose -f docker-compose.prod.yml down -v   # remove os volumes (apaga tudo, inclusive uploads)
docker compose -f docker-compose.prod.yml up -d --build
```
