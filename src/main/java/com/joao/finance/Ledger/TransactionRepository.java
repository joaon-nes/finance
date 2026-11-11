package com.joao.finance.Ledger;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface TransactionRepository extends JpaRepository<Transaction, UUID> {
    //usado na idempotencia, procura se usuário já enviou a chave antes de executar uma operação
    // devolve transação original se encontrar e não cria outra
    // busca incluí usuário pq chave é única por usuário (UNIQUE + user_id + chave)
    Optional<Transaction> findByUserAndIdempotencyKey(UUID userId, String idempotencyKey);
}
