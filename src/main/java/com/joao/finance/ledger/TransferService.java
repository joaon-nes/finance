package com.joao.finance.ledger;

import com.joao.finance.account.Account;
import com.joao.finance.account.AccountRepository;
import com.joao.finance.common.BusinessException;
import com.joao.finance.common.NotFoundException;
import com.joao.finance.user.User;
import com.joao.finance.user.UserRepository;

// tratar erros quando o banco não aceita um dado, como registros duplicados
import org.springframework.dao.DataIntegrityViolationException;
// identifica quando outra operação altera a conta ao mesmo tempo
import org.springframework.orm.ObjectOptimisticLockingFailureException;
// indica ao Spring que esta classe é um serviço
import org.springframework.stereotype.Service;
// ajuda a controlar transações, confirmando ou desfazendo alterações
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;


    // @Service marca a classe como serviço (regras de negócio)
@Service
public class TransferService {

    private static final int MAX_ATTEMPTS = 3;

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final UserRepository userRepository;
    private final TransactionTemplate transactionTemplate;

    // injeção por construtor, nd de "new" repositories
    // spring vê o que o construtor pede e entrega as instâncias prontas
    public TransferService (AccountRepository accountRepository, TransactionRepository transactionRepository, 
                            LedgerEntryRepository ledgerEntryRepository, UserRepository userRepository, 
                            TransactionTemplate transactionTemplate) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.userRepository = userRepository;
        this.transactionTemplate = transactionTemplate;
    }

    // record = classe imutável para agrupar dados
    // O java gerar construtor e métodos de leitura (cmd.userId(), cmd.amountCents()...) sem get (evita método com parâmetros soltos)
    public record Command (UUID userId, UUID fromAccountId, UUID toAccountId, long amountCents, String description, LocalDate occurredOn, String idempotencyKey) {}
        // método não transacional, retry fica fora da transação que falhou (esta foi marcada para rollback e não pode ser reaproveitada)
        // transação é aberta por baixo com TransactionTemplate a cada tentativa
        // @Transactional não funcionaria para chamar um método @Transactional da mesma classe e não passa pelo proxy do spring, anotação seria ignorada
        // devolve a transaction gravada, fora da transação, campos LAZY (getUser()) não podem ser acessados (LazyInitializationException)
        public Transaction transfer (Command cmd) {
            // validação que não precisam do banco vem primeiro
            if (cmd.amountCents () <= 0) {
                throw new BusinessException("O valor da transferência deve ser positivo");
            } if (cmd.fromAccountId().equals(cmd.toAccountId())) {
                throw new BusinessException("As contas de origem e destino devem ser diferentes");
            }
            // idempotência: se a chave já foi usada devolve a operação original em vez de executar de novo
            if (cmd.idempotencyKey() != null) {
                Optional<Transaction> existing = transactionRepository.findByUserIdAndIdempotencyKey(cmd.userId(), cmd.idempotencyKey());
                if (existing.isPresent()) {
                    return existing.get();
                }
            }

        // laço de tentativas
        for (int attempt = 1; ; attempt++) {
            try {
                // execute abre a transação, roda o lambda e dá COMMIT, para exceção, ROLLBACK
                // status > é um lambda, função anonima que recebe o status da transação
                return transactionTemplate.execute(status -> doTransfer(cmd));
            } catch (ObjectOptimisticLockingFailureException e) {
                // LOCK OTIMISTA: outra operação alterou uma das contas (@Version) entre leitura e gravação
                // na nova tentativa as contas serão relidas com saldo atualizado e a validação do saldo roda novamente
                if (attempt >= MAX_ATTEMPTS) {
                    throw new BusinessException("Conta em uso por outra operação, tente novamente");
                }
                // sem throw o laço volta ao início e tenta de novo
            } catch (DataIntegrityViolationException e) {
                // idempotência: duas requisições com mesma chave passaram pela checagem acima e o UNIQUE do banco barrou a segunda, devolve a original
                if (cmd.idempotencyKey() != null) {
                    // orElseThrow com lambda, se não achar relança o erro original
                    return transactionRepository.findByUserIdAndIdempotencyKey(cmd.userId(), cmd.idempotencyKey()).orElseThrow(() -> e);
                }
                throw e;
            }
        }
    }
    // roda dentro da transação aberta pelo transer(): grava tudo ou não grava nada
    private Transaction doTransfer (Command cmd) {
        // busca pelo id e pelo dono, conta de outro usuário finge que não existe
        Account from = accountRepository.findByIdAndUserId(cmd.fromAccountId(), cmd.userId()).orElseThrow(() -> new NotFoundException("Conta de origem não encontrada"));
        Account to = accountRepository.findByIdAndUserId(cmd.toAccountId(), cmd.userId()).orElseThrow(() -> new NotFoundException("Conta de destino não encontrada"));
        
        if (!from.isActive() || !to.isActive()) {
            throw new BusinessException("Não é possível transferir com conta invativa");
        }
        // cartão de crédito pode ficar negativo (dívida), contas não
        if (from.getType() != Account.Type.CREDIT_CARD && from.getBalanceCents() < cmd.amountCents()) {
            throw new BusinessException("Saldo insuficiente");
        }
        // getReferenceById devolve ponteiro para o usuário sem fazer select, transaction só precisa do id para preencher chave estrangeira
        User user = userRepository.getReferenceById(cmd.userId());

        Transaction tx = transactionRepository.save(new Transaction(user, Transaction.Type.TRANSFER, cmd.description(), cmd.occurredOn(), cmd.idempotencyKey()));
        // partida dobrada: lançamento negativo na origem e positivo no destino, soma sempre = 0
        ledgerEntryRepository.save(new LedgerEntry(tx, from, -cmd.amountCents()));
        ledgerEntryRepository.save(new LedgerEntry(tx, to, cmd.amountCents()));

        // atualiza saldo em cache, não há save(), contas lidas dentro dessa transação e o hibernate detecta mudança (dirty checking)
        // grava o update ... where version = ? no commit, update dispara o lock otimista
        from.apply(-cmd.amountCents());
        to.apply(cmd.amountCents());
        return tx;
        }
    }
