package com.joao.finance.ledger;

import com.joao.finance.TestcontainersConfiguration;
import com.joao.finance.account.Account;
import com.joao.finance.account.AccountRepository;
import com.joao.finance.common.NotFoundException;
import com.joao.finance.common.PageResponse;
import com.joao.finance.user.User;
import com.joao.finance.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
// PageRequest monta o Pageable na mão (no controller quem monta é o spring a partir de ?page= e ?size=)
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
// assertThatCode(...).doesNotThrowAnyException(): confirma que o código roda sem lançar exceção
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// TESTE DE INTEGRAÇÃO do extrato: PostgreSQL real (Testcontainers), lançamentos criados pelos repositories (pela API ainda não há como criar lançamentos; receitas e despesas entram na etapa 3)
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class StatementServiceIntegrationTest {

    @Autowired StatementService statementService;
    @Autowired UserRepository userRepository;
    @Autowired AccountRepository accountRepository;
    @Autowired TransactionRepository transactionRepository;
    @Autowired LedgerEntryRepository ledgerEntryRepository;

    private User user;
    private UUID accountId;
    private UUID otherUsersAccountId;

    // banco limpo a cada teste, ordem de apagar: quem tem chave estrangeira primeiro
    @BeforeEach
    void setUp () {
        ledgerEntryRepository.deleteAll();
        transactionRepository.deleteAll();
        accountRepository.deleteAll();
        userRepository.deleteAll();

        user = userRepository.save(new User("Teste", "teste@exemplo.com", "hash"));
        accountId = accountRepository.save(new Account(user, "Corrente", Account.Type.CHECKING)).getId();

        // segundo usuário, para testar que o extrato de conta alheia não é acessível
        // mesmo nome de conta é permitido: o UNIQUE é por usuário (user_id, name)
        User other = userRepository.save(new User("Outro", "outro@exemplo.com", "hash"));
        otherUsersAccountId = accountRepository.save(new Account(other, "Corrente", Account.Type.CHECKING)).getId();
    }

    // cria uma receita do jeito certo: transação + lançamento no ledger + saldo em cache atualizado
    // será substituído pelo service de receitas quando a etapa 3 existir
    private void income (long cents, String description) {
        Account account = accountRepository.findById(accountId).orElseThrow();
        Transaction tx = transactionRepository.save(new Transaction(
                user, Transaction.Type.INCOME, description, LocalDate.now(), null));
        ledgerEntryRepository.save(new LedgerEntry(tx, account, cents));
        account.apply(cents);
        accountRepository.save(account);
    }

    @Test
    void extratoPaginado_trazPaginaPedidaComTotaisCorretos () {
        for (int i = 1; i <= 5; i++) {
            income(1_000L * i, "Receita " + i);
        }

        PageResponse<StatementEntryResponse> first = statementService.statement(user.getId(), accountId, PageRequest.of(0, 2));
        assertThat(first.content()).hasSize(2);
        assertThat(first.page()).isEqualTo(0);
        assertThat(first.size()).isEqualTo(2);
        assertThat(first.totalElements()).isEqualTo(5);
        assertThat(first.totalPages()).isEqualTo(3);

        // última página: sobra 1 lançamento (5 = 2 + 2 + 1)
        PageResponse<StatementEntryResponse> last = statementService.statement(user.getId(), accountId, PageRequest.of(2, 2));
        assertThat(last.content()).hasSize(1);
    }

    @Test
    void extrato_vemDoMaisRecenteParaOMaisAntigo_comDadosDaTransacao () {
        income(1_000, "Receita 1");
        income(2_000, "Receita 2");
        income(3_000, "Receita 3");

        List<StatementEntryResponse> content =
                statementService.statement(user.getId(), accountId, PageRequest.of(0, 10)).content();

        assertThat(content).hasSize(3);
        // isSortedAccordingTo confere a ordem sem depender de valores exatos
        // (dois lançamentos podem ter o mesmo instante; empate não quebra o teste)
        assertThat(content).isSortedAccordingTo(
                Comparator.comparing(StatementEntryResponse::createdAt).reversed());
        // dados que vêm da Transaction (tipo e descrição) chegaram no DTO
        assertThat(content).allSatisfy(entry -> {
            assertThat(entry.type()).isEqualTo(Transaction.Type.INCOME);
            assertThat(entry.description()).startsWith("Receita");
            assertThat(entry.amountCents()).isPositive();
        });
    }

    @Test
    void sortEnviadoPeloCliente_eDescartado () {
        income(1_000, "Receita");

        // propriedade inexistente, se o sort chegasse ao repository a consulta falharia (PropertyReferenceException)
        PageRequest withSort = PageRequest.of(0, 10, Sort.by("propriedadeInexistente"));

        assertThatCode(() -> statementService.statement(user.getId(), accountId, withSort))
                .doesNotThrowAnyException();
    }

    @Test
    void contaDeOutroUsuario_respondeNaoEncontrada () {
        assertThatThrownBy(() -> statementService.statement(user.getId(), otherUsersAccountId, PageRequest.of(0, 10)))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Conta não encontrada");
    }

    @Test
    void entityGraph_carregaATransacaoJuntoComOLancamento () {
        income(1_000, "Receita");

        // chamada direta ao repository FORA de transação (esse teste não é @Transactional), a sessão do hibernate fecha assim que o método do repository termina 
        // sem o @EntityGraph, getTransaction() seria um proxy LAZY vazio e getDescription() lançaria LazyInitializationException, com ele a Transaction já veio no mesmo select
        List<LedgerEntry> entries = ledgerEntryRepository
                .findByAccountIdOrderByCreatedAtDesc(accountId, PageRequest.of(0, 10))
                .getContent();

        assertThat(entries).hasSize(1);
        assertThat(entries.get(0).getTransaction().getDescription()).isEqualTo("Receita");
    }
}