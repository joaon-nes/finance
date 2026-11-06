# Finance

Gestor financeiro pessoal: API REST em Java e Spring Boot para controle de contas, lançamentos e transferências, construída em torno de um **ledger (livro-razão) append-only** sobre PostgreSQL.

> **Status:** em desenvolvimento. Este repositório é um projeto de estudo e portfólio; o roadmap abaixo mostra o que já está pronto e o que vem a seguir.

## Objetivos do projeto

- Modelar dados financeiros com integridade: dinheiro em centavos, lançamentos imutáveis e saldo derivado do ledger.
- Tratar concorrência e repetição de requisições (lock otimista e idempotência) em transferências.
- Manter o schema versionado (Flyway) e reproduzível com um comando (Docker Compose).
- Cobrir as regras críticas com testes automatizados e CI.

## Stack

| Camada | Tecnologia |
|---|---|
| Linguagem | Java 21 |
| Framework | Spring Boot 4.1.2 (Web, Data JPA, Validation) |
| Banco de dados | PostgreSQL 16 |
| Migrations | Flyway |
| Build | Maven (Maven Wrapper) |
| Infraestrutura local | Docker e Docker Compose |

**Planejado:** Spring Security + JWT, JUnit 5, Mockito, Testcontainers, GitHub Actions, OpenAPI/Swagger, módulo em C++ para importação de extratos e um front-end simples.

## Arquitetura

Monólito modular em Spring Boot, organizado por domínio (e não por camada técnica):

```
┌────────────┐   REST/JSON   ┌──────────────────────────┐      ┌──────────────┐
│  Cliente   │ ────────────► │  Spring Boot (monólito)  │ ───► │  PostgreSQL  │
│ (front/API)│               │  user | account | ledger │      └──────────────┘
└────────────┘               │  budgets | recurring ... │
                             └──────────────────────────┘
```

Estrutura de pacotes atual:

```
src/main/java/com/joao/finance/
├── FinanceApplication.java
├── user/       User, UserRepository
├── account/    Account, AccountRepository
└── ledger/     (em construção) Transaction, LedgerEntry
```

## Modelo de dados

O schema inicial está em `src/main/resources/db/migration/V1__init.sql` e contém 9 tabelas: `users`, `accounts`, `categories`, `transactions`, `ledger_entries`, `import_batches`, `budgets`, `recurring_rules` e `audit_log`, além da view `v_balance_reconciliation`.

Principais decisões:

- **Dinheiro em centavos (`BIGINT`)**, nunca `float`, para evitar erro de arredondamento.
- **Ledger append-only:** lançamentos não são editados nem apagados; erros se corrigem com estorno.
- **Transferência = 1 transação com 2 lançamentos** (débito e crédito), cuja soma é sempre zero.
- **Saldo da conta é um cache** do ledger, protegido por lock otimista (`@Version`), com uma view que detecta divergências.
- **Idempotência** por `idempotency_key` e deduplicação de importações por `external_hash`, ambas garantidas por constraints `UNIQUE` no banco.
- **UUID** como chave primária e `TIMESTAMPTZ` para instantes, `DATE` para datas de negócio.

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

3. A aplicação sobe em `http://localhost:8080`.

Para recriar o banco do zero (apaga os dados): `docker compose down -v && docker compose up -d`.

## Roadmap (ordem cronológica)

### Concluído

- [x] Definição do projeto, da stack e da arquitetura (monólito modular)
- [x] Modelagem do banco de dados (schema V1 com ledger, orçamentos, recorrências e auditoria)
- [x] PostgreSQL em Docker Compose
- [x] Projeto Spring Boot (Java 21, Maven) conectado ao banco
- [x] Migration inicial com Flyway aplicada
- [x] Entidades `User` e `Account` (com lock otimista) e seus repositories
- [x] Repositório versionado no GitHub

### Próximos passos

- [ ] Entidades `Transaction` e `LedgerEntry` e seus repositories
- [ ] `AccountService` e endpoints de contas (com usuário de teste fixo)
- [ ] `TransferService`: transação, lock otimista com retry e idempotência
- [ ] Testes de invariantes com Testcontainers (soma zero, saldo igual ao ledger, transferências concorrentes)
- [ ] Autenticação e autorização (Spring Security + JWT)
- [ ] Categorias, orçamentos e relatórios
- [ ] Lançamentos recorrentes
- [ ] Módulo em C++ para importação de extratos (CSV/OFX)
- [ ] Pipeline de CI (GitHub Actions)
- [ ] Documentação da API (OpenAPI/Swagger)
- [ ] Front-end simples
- [ ] Deploy na nuvem

## Autor

João Nunes, [@joaon-nes](https://github.com/joaon-nes)
