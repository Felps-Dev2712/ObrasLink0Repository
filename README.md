# Obras Link

Plataforma web para cadastro de prestadores de serviço e clientes, com vitrine para solicitação e anúncio de serviços de construção civil.

## Stack Tecnológica

- **Backend**: Java 21 + Spring Boot 3.2.2
- **Frontend**: htmx + Pure CSS
- **Banco de Dados**: PostgreSQL 16
- **Containerização**: Docker + Docker Compose

## Funcionalidades Planejadas

- Cadastro de prestadores de serviço (pedreiros, mestres de obras, empreiteiras)
- Cadastro de clientes
- Catálogo de serviços de construção civil
- Sistema de busca e solicitação de serviços
- CRUD completo para todas as entidades

## Pré-requisitos

- Docker
- Docker Compose

**Nota**: Não é necessário ter Java ou Maven instalados localmente, pois tudo roda em containers.

## Como Executar

### 1. Clone o repositório ou navegue até o diretório do projeto

```bash
cd obras-link
```

### 2. Inicie os containers

```bash
docker-compose up --build
```

Este comando irá:
- Baixar a imagem do PostgreSQL 16
- Compilar a aplicação Java usando Maven
- Criar os containers para o banco de dados e aplicação
- Iniciar os serviços

### 3. Acesse a aplicação

Abra o navegador e acesse:
```
http://localhost:8080
```

### 4. Para parar os containers

```bash
docker-compose down
```

### 5. Para parar e remover os volumes (apaga os dados do banco)

```bash
docker-compose down -v
```

## Estrutura do Projeto

```
obras-link/
├── src/
│   ├── main/
│   │   ├── java/com/obraslink/
│   │   │   ├── ObrasLinkApplication.java
│   │   │   └── controller/
│   │   │       └── HomeController.java
│   │   └── resources/
│   │       ├── application.properties
│   │       ├── templates/
│   │       │   └── index.html
│   │       └── static/
│   │           └── css/
│   └── test/
├── Dockerfile
├── docker-compose.yml
├── pom.xml
└── README.md
```

## Configuração do Banco de Dados

As configurações do banco de dados estão definidas no `docker-compose.yml`:

- **Database**: obraslink
- **User**: obraslink
- **Password**: obraslink123
- **Port**: 5432

## Desenvolvimento

### Logs da aplicação

```bash
docker-compose logs -f app
```

### Logs do banco de dados

```bash
docker-compose logs -f db
```

### Acessar o container da aplicação

```bash
docker exec -it obras-link-app sh
```

### Acessar o PostgreSQL

```bash
docker exec -it obras-link-db psql -U obraslink -d obraslink
```

## Próximos Passos

1. Implementar modelos de dados (Prestador, Cliente, Serviço)
2. Criar controllers REST
3. Implementar views com htmx
4. Adicionar validações e regras de negócio
5. Implementar autenticação e autorização
