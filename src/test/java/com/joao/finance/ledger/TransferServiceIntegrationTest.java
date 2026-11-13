package com.joao.finance.ledger;

import com.joao.finance.TestcontainersConfiguration;
import com.joao.finance.account.Account;
import com.joao.finance.account.AccountRepository;
import com.joao.finance.common.BusinessException;
import com.joao.finance.user.User;
import com.joao.finance.user.UserRepository;
// JUnir 5 @Test marca um método como teste, @BeforeEach roda antes de cada teste
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

// AssertJ: biblioteca de verificacções com leitura: assertThat(valor).isEqualTo(separado)
// import static permite escrever assertThat(...) sem prefixo Assertions
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// TESTE DE INTEGRAÇÃO: sobe a aplicação inteira (Spring + Hibernate + Flyway) contra um
// PostgreSQL real, em vez de testar uma classe isolada com dados falsos
// @SpringBootTest sobe o contexto do Spring; @Import(...) traz o contêiner descartável da TestcontainersConfiguration. O Docker Desktop precisa estar aberto
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class TransferServiceIntegrationTest {
 
    // @Autowired pede ao Spring que entregue o objeto pronto 
    // Em testes é o jeito comum (no código normal, usamos injeção pelo construtor)
    @Autowired TransferService transferService;
    @Autowired UserRepository userRepository;
    @Autowired AccountRepository accountRepository;
    @Autowired TransactionRepository transactionRepository;
    @Autowired LedgerEntryRepository ledgerEntryRepository;
 
    private User user;
    private UUID checkingId;
    private UUID savingsId;
 
    // Garante que cada teste começa com o banco vazio, sem depender da ordem de execução.
    // A ordem de apagar importa: primeiro quem tem chave estrangeira (lançamentos), por último quem é referenciado (usuários).
    @BeforeEach
    void setUp() {
        ledgerEntryRepository.deleteAll();
        transactionRepository.deleteAll();
        accountRepository.deleteAll();
        userRepository.deleteAll();
 
        user = userRepository.save(new User("Teste", "teste@exemplo.com", "hash"));
        checkingId = accountRepository.save(new Account(user, "Corrente", Account.Type.CHECKING)).getId();
        savingsId = accountRepository.save(new Account(user, "Poupança", Account.Type.SAVINGS)).getId();
    }
 
    // Cria o saldo inicial do jeito certo: um lançamento de receita no ledger E o saldo em cache
    // atualizado, para os dois continuarem batendo. Valores em centavos (100_000 = R$ 1.000,00; o "_" é só separador visual do Java)
    private void deposit(UUID accountId, long cents) {
        Account account = accountRepository.findById(accountId).orElseThrow();
        Transaction tx = transactionRepository.save(new Transaction(
                user, Transaction.Type.INCOME, "Depósito inicial", LocalDate.now(), null));
        ledgerEntryRepository.save(new LedgerEntry(tx, account, cents));
        account.apply(cents);
        accountRepository.save(account);
    }
 
    private TransferService.Command command(long cents, String idempotencyKey) {
        return new TransferService.Command(user.getId(), checkingId, savingsId,
                cents, "Guardar dinheiro", LocalDate.now(), idempotencyKey);
    }
 
    private long balanceOf(UUID accountId) {
        return accountRepository.findById(accountId).orElseThrow().getBalanceCents();
    }
 
    // Nome do teste descreve o cenário e resultado esperado
    @Test
    void transferenciaValida_movimentaSaldosEMantemOLedgerConsistente() {
        deposit(checkingId, 100_000);
 
        Transaction tx = transferService.transfer(command(30_000, null));
 
        assertThat(tx.getType()).isEqualTo(Transaction.Type.TRANSFER);
        assertThat(balanceOf(checkingId)).isEqualTo(70_000);
        assertThat(balanceOf(savingsId)).isEqualTo(30_000);
 
        // INVARIANTE 1: o saldo em cache bate com a soma do ledger, em cada conta.
        assertThat(ledgerEntryRepository.sumByAccountId(checkingId)).isEqualTo(balanceOf(checkingId));
        assertThat(ledgerEntryRepository.sumByAccountId(savingsId)).isEqualTo(balanceOf(savingsId));
 
        // INVARIANTE 2: soma zero. O depósito é o único dinheiro "novo" (100_000) se o total do ledger continua 100_000, os dois lançamentos da transferência se anularam.
        List<LedgerEntry> entries = ledgerEntryRepository.findAll();
        assertThat(entries).hasSize(3); // 1 depósito + 2 da transferência
        long total = entries.stream().mapToLong(LedgerEntry::getAmountCents).sum();
        assertThat(total).isEqualTo(100_000);
    }
 
    @Test
    void saldoInsuficiente_recusaENadaEGravado() {
        deposit(checkingId, 10_000);
 
        // assertThatThrownBy executa o código e confirma que ele lançou a exceção esperada
        assertThatThrownBy(() -> transferService.transfer(command(50_000, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Saldo insuficiente");
 
        // ROLLBACK/validação: saldos intactos e só existe a transação do depósito
        assertThat(balanceOf(checkingId)).isEqualTo(10_000);
        assertThat(balanceOf(savingsId)).isEqualTo(0);
        assertThat(transactionRepository.count()).isEqualTo(1);
        assertThat(ledgerEntryRepository.count()).isEqualTo(1);
    }
 
    @Test
    void mesmaChaveDeIdempotencia_debitaUmaSoVez() {
        deposit(checkingId, 100_000);
 
        Transaction first = transferService.transfer(command(30_000, "chave-123"));
        Transaction second = transferService.transfer(command(30_000, "chave-123"));
 
        // segunda chamada devolve a operação original em vez de executar outra
        assertThat(second.getId()).isEqualTo(first.getId());
        assertThat(balanceOf(checkingId)).isEqualTo(70_000); // debitou só uma vez
        assertThat(balanceOf(savingsId)).isEqualTo(30_000);
        assertThat(transactionRepository.count()).isEqualTo(2); // depósito + 1 transferência
    }
}