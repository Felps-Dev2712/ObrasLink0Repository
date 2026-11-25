# ObraLink

Plataforma web que conecta **clientes** a **prestadores de serviço** da construção civil (pedreiros,
eletricistas, pintores, encanadores, marceneiros, gesseiros, mestres de obra e empreiteiras). O cliente
encontra profissionais por especialidade e região, compara avaliações e fala direto com eles pelo
WhatsApp — sem intermediação de pagamento.

## Funcionalidades

**Contas e acesso**
- Cadastro como cliente, como prestador ou com os dois perfis na mesma conta (com troca entre eles).
- Login com e-mail/senha, "Adicionar conta" para trocar entre contas já logadas no mesmo navegador.
- Aceite obrigatório e versionado dos Termos de Uso e do Aviso de Privacidade, com nova confirmação
  sempre que os documentos forem atualizados.
- Exclusão de conta pelo próprio usuário, a qualquer momento.

**Vitrine de profissionais e serviços**
- Busca e filtro por especialidade, nome, distância e região de atendimento (mapa com localização
  aproximada, nunca o endereço exato).
- Perfil público do profissional com especialidades (principal e secundárias), serviços, preço,
  disponibilidade semanal, certificações aprovadas e avaliações recebidas.
- Favoritos.

**Contato e atendimento**
- Solicitação de contato que abre o WhatsApp com uma mensagem pronta, sem expor o atendimento internamente.
- Acompanhamento do atendimento (solicitado → em andamento agendado → concluído) e reagendamento pelo
  prestador.
- **Pedido de orçamento a vários prestadores de uma vez**: o cliente seleciona um ou mais profissionais
  da mesma especialidade (ou de especialidades em comum, quando um prestador atua em mais de uma),
  preenche um único formulário — com campos próprios de cada especialidade escolhida — confirma ou troca
  o endereço de execução (sem alterar o endereço do perfil) e revisa, antes de enviar, uma mensagem
  individual e editável para cada profissional.
- Avaliação mútua (cliente avalia o prestador e vice-versa) depois de um atendimento concluído.
- Denúncia de um participante pelo outro, com fila de análise para o administrador.

**Prestador**
- Painel com indicadores do período, gráfico de atendimentos por status e histórico.
- Catálogo de serviços, agenda semanal de disponibilidade e envio de certificações para análise.

**Administração**
- Revisão de certificações e denúncias pendentes.

**Personalização**
- Tema claro/escuro em todas as telas (inclusive dentro do painel do prestador).
- Mascote animado ("Tchuros") na página inicial a cada login.

## Stack tecnológica

- **Backend**: Java 21, Spring Boot 3.2.2 (Web, Security, Data JPA, Validation)
- **Views**: Thymeleaf, CSS e JavaScript próprios (sem framework de frontend)
- **Banco de dados**: PostgreSQL 16, schema aplicado por `ddl-auto=update` (sem ferramenta de migração)
- **Testes**: JUnit 5 + Mockito, testes de tela com MockMvc
- **Containerização**: Docker + Docker Compose

## Rodar localmente (desenvolvimento)

Pré-requisitos: Docker e Docker Compose. Não é preciso ter Java ou Maven instalados — tudo roda em container.

```bash
docker compose up --build
```

Isso baixa o Postgres 16, compila a aplicação e sobe os dois containers. Acesse em
**http://localhost:8080**.

Ao subir com o banco vazio, o `DataSeeder` cria contas e dados fictícios para teste — veja
[docs/contas-de-teste.md](docs/contas-de-teste.md) para a lista de contas, senhas e o que testar com
cada uma.

Outros comandos úteis:

```bash
docker compose logs -f app          # logs da aplicação
docker compose down                 # para os containers
docker compose down -v              # para e apaga os dados do banco e dos uploads
```

## Publicar uma instância pública (demonstração/produção)

Há um segundo arquivo de Compose, `docker-compose.prod.yml`, para rodar numa máquina própria (por
exemplo uma VM gratuita da Oracle Cloud, com servidor no Brasil) em vez de só localmente:

- Ativa o perfil Spring `demo`, que usa o `DemoSeeder` em vez do `DataSeeder` — dados de demonstração
  com nomes e e-mails realistas (sem "Teste"), cobrindo as mesmas funcionalidades. Veja
  [docs/contas-demo.md](docs/contas-demo.md).
- A senha do Postgres vem de uma variável de ambiente, não fica fixa no arquivo.
- A porta do banco não é exposta para fora da máquina; só a porta 8080 (a da aplicação) precisa ficar
  acessível.
- Os containers reiniciam sozinhos se a máquina reiniciar ou o processo cair (`restart: unless-stopped`).

Para usar:

```bash
# na máquina onde vai rodar, ao lado deste arquivo:
echo "POSTGRES_PASSWORD=escolha-uma-senha-forte-aqui" > .env

docker compose -f docker-compose.prod.yml up -d --build
```

Depois, abra a porta 8080 no firewall da máquina (e, na Oracle Cloud, também na lista de segurança da
rede/VCN). Para recriar os dados do zero, veja a seção "Recriar os dados" em
[docs/contas-demo.md](docs/contas-demo.md).

Alternativa mais simples (sem precisar de uma VM própria): o repositório já tem um `Dockerfile` pronto
para serviços como o [Render](https://render.com), que builda e hospeda a aplicação automaticamente a
partir do GitHub — mais fácil de configurar, mas sem servidor no Brasil e com o plano gratuito "dormindo"
depois de um tempo sem acesso.

## Testes

```bash
# dentro de um container com Maven (não precisa instalar Maven localmente):
docker run --rm -v "$(pwd):/app" -w /app maven:3.9.6-eclipse-temurin-21-alpine mvn test
```

Os testes seguem o padrão de um teste por classe de serviço/controller, no mesmo pacote da classe
testada, com JUnit 5 e Mockito; testes de tela usam MockMvc sobre a configuração real do Spring Security.

## Estrutura do projeto

```
src/main/java/com/obraslink/
├── config/        # Seeders de dados (DataSeeder, DemoSeeder) e configurações gerais
├── controller/    # Controllers MVC (um por área: prestadores, serviços, atendimentos, orçamentos...)
├── dto/           # Formulários e requests que não são entidades
├── model/         # Entidades JPA
├── repository/     # Repositórios Spring Data JPA
├── security/      # Spring Security: filtros, handlers de autenticação, aceite de termos
├── service/       # Regras de negócio
└── validation/    # Validação e normalização de CPF/CNPJ/telefone

src/main/resources/
├── templates/     # Views Thymeleaf (uma pasta por área, + fragments/ e legal/<versão>/)
└── static/        # CSS, JavaScript e imagens

docs/              # Documentação de funcionalidades específicas (ver abaixo)
```

## Documentação adicional

- [docs/contas-de-teste.md](docs/contas-de-teste.md) — contas do ambiente de desenvolvimento local.
- [docs/contas-demo.md](docs/contas-demo.md) — contas da instância pública de demonstração.
- [docs/atendimentos.md](docs/atendimentos.md) — regras do contato por WhatsApp e do acompanhamento do atendimento.
- [docs/avaliacoes.md](docs/avaliacoes.md) — regras da avaliação mútua entre cliente e prestador.
- [docs/validacao-cadastros.md](docs/validacao-cadastros.md) — validação de CPF/CNPJ/telefone.
- [docs/termos-e-privacidade.md](docs/termos-e-privacidade.md) — versionamento e aceite dos Termos de Uso e do Aviso de Privacidade.
