# Finance

Gestor financeiro pessoal: API REST em Java e Spring Boot para controle de contas, lançamentos e transferências entre as próprias contas, construída em torno de um **ledger (livro-razão) append-only** sobre PostgreSQL.

> **Status:** em desenvolvimento. O núcleo de dados e o serviço de transferência já estão implementados e testados com banco real; a API HTTP e a autenticação ainda estão no roadmap. Este repositório é um projeto de estudo e portfólio.

## Objetivos do projeto

- Modelar dados financeiros com integridade: dinheiro em centavos, lançamentos imutáveis e saldo derivado do ledger.
- Tratar concorrência e repetição de requisições (lock otimista e idempotência) nas transferências.
- Manter o schema versionado (Flyway) e reproduzível com um comando (Docker Compose).
- Cobrir as regras críticas com testes de integração e CI.

## Stack

| Camada | Tecnologia |
|---|---|
| Linguagem | Java 21 |
| Framework | Spring Boot 4.1.2 (Web, Data JPA, Validation) |
| Banco de dados | PostgreSQL 16 |
| Migrations | Flyway |
| Testes | JUnit 5, AssertJ, Testcontainers |
| Build | Maven (Maven Wrapper) |
| Infraestrutura local | Docker e Docker Compose |

**Planejado:** Spring Security + JWT, OpenAPI/Swagger, GitHub Actions (CI), módulo em C++ para importação de extratos e front-end em Angular.

## Arquitetura

Monólito modular em Spring Boot, organizado por domínio (e não por camada técnica):

```
┌────────────┐   REST/JSON   ┌──────────────────────────┐      ┌──────────────┐
│  Cliente   │ ────────────► │  Spring Boot (monólito)  │ ───► │  PostgreSQL  │
│ (planejado)│               │  user | account | ledger │      └──────────────┘
└────────────┘               └──────────────────────────┘
```

Estrutura atual do código:

```
src/main/java/com/joao/finance/
├── FinanceApplication.java
├── user/       User, UserRepository
├── account/    Account, AccountRepository
├── ledger/     Transaction, LedgerEntry, TransferService e os repositories
└── common/     BusinessException, NotFoundException

src/test/java/com/joao/finance/
├── TestcontainersConfiguration.java
└── ledger/TransferServiceIntegrationTest.java
```

## Modelo de dados

O schema está em `src/main/resources/db/migration/V1__init.sql`: 9 tabelas (`users`, `accounts`, `categories`, `transactions`, `ledger_entries`, `import_batches`, `budgets`, `recurring_rules` e `audit_log`) e a view `v_balance_reconciliation`. Hoje o código mapeia `users`, `accounts`, `transactions` e `ledger_entries`; as demais tabelas já existem no banco e serão mapeadas nas próximas etapas.

Principais decisões:

- **Dinheiro em centavos (`BIGINT`)**, nunca `float`, para evitar erro de arredondamento.
- **Ledger append-only:** lançamentos não são editados nem apagados; erros se corrigem com estorno (planejado).
- **Transferência = 1 transação com 2 lançamentos** (débito e crédito), cuja soma é sempre zero.
- **Saldo da conta é um cache** do ledger, protegido por lock otimista (`@Version`), com uma view que detecta divergências.
- **Idempotência** por `idempotency_key` e deduplicação de importações por `external_hash`, ambas garantidas por constraints `UNIQUE` no banco.
- **UUID** como chave primária, `TIMESTAMPTZ` para instantes e `DATE` para datas de negócio.

## Regras implementadas: transferência entre contas

O `TransferService` move dinheiro entre contas do mesmo usuário, como registro de controle (não há integração bancária). Para cada transferência:

- o valor deve ser positivo e as contas de origem e destino devem ser diferentes;
- as contas precisam pertencer ao usuário e estar ativas;
- a origem precisa ter saldo suficiente (somente cartão de crédito pode ficar negativo);
- tudo é gravado em **uma única transação do banco**: a operação, os dois lançamentos e os dois saldos, ou nada;
- conflito de concorrência (lock otimista) provoca **nova tentativa**, até 3 vezes;
- uma mesma `idempotency_key` nunca executa duas vezes: a segunda chamada devolve a operação original.

## Testes

Os testes de integração sobem um **PostgreSQL real em contêiner descartável** (Testcontainers), com o schema criado pelo Flyway, sem tocar no banco de desenvolvimento. Cenários cobertos hoje:

- transferência válida: saldos corretos, saldo em cache igual à soma do ledger e lançamentos que somam zero;
- saldo insuficiente: a transferência é recusada e nada é gravado;
- mesma chave de idempotência repetida: o débito acontece uma única vez;
- transferências simultâneas (10 threads na mesma conta): nenhum dinheiro é criado ou perdido e o saldo em cache continua igual ao ledger.

```bash
./mvnw test        # no Windows PowerShell: .\mvnw test (o Docker precisa estar aberto)
```

## Como executar

**Pré-requisitos:** JDK 21 e Docker Desktop.

1. Suba o PostgreSQL:

   ```bash
   docker compose up -d
   docker compose ps   # aguarde o status "healthy"
   ```

   O banco fica exposto em `localhost:5433` (banco `finance`, usuário `finance`). As credenciais do `docker-compose.yml` e do `application.properties` são **apenas para desenvolvimento local**.

2. Rode a aplicação (o Flyway aplica o schema automaticamente na primeira execução):

   ```bash
   ./mvnw spring-boot:run      # no Windows PowerShell: .\mvnw spring-boot:run
   ```

3. A aplicação sobe em `http://localhost:8080`. Ainda não há endpoints HTTP.

Para recriar o banco do zero (apaga os dados): `docker compose down -v && docker compose up -d`.

## Roadmap

### Concluído

- [x] Definição do projeto, da stack e da arquitetura (monólito modular)
- [x] Modelagem do banco de dados e migration inicial (Flyway)
- [x] PostgreSQL em Docker Compose
- [x] Projeto Spring Boot (Java 21, Maven) conectado ao banco
- [x] Entidades `User`, `Account`, `Transaction` e `LedgerEntry` e seus repositories
- [x] Exceções de negócio (`BusinessException`, `NotFoundException`)
- [x] `TransferService`: transação, lock otimista com retry e idempotência
- [x] Testes de integração com Testcontainers (4 cenários, incluindo transferências simultâneas)
- [x] Repositório versionado no GitHub

### Próximos passos: MVP do backend

- [ ] `AccountService` e endpoints de contas (criar, listar e extrato paginado)
- [ ] DTOs, validação (Bean Validation) e tratamento global de erros (`@RestControllerAdvice`)
- [ ] Registro de receitas e despesas
- [ ] Estorno de transações
- [ ] Autenticação e autorização (Spring Security + JWT)

### Depois do MVP

- [ ] Pipeline de CI (GitHub Actions)
- [ ] Documentação da API (OpenAPI/Swagger)
- [ ] Categorias, orçamentos e relatórios
- [ ] Lançamentos recorrentes
- [ ] Importação de extratos (CSV/OFX) com módulo em C++
- [ ] Auditoria das ações importantes
- [ ] Configuração por variáveis de ambiente e Dockerfile da aplicação
- [ ] Front-end em Angular
- [ ] Deploy na nuvem

## Autor

João Nunes, [@joaon-nes](https://github.com/joaon-nes)
