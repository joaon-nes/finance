package com.joao.finance.Ledger;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.UUID;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, UUID>{
    // extrato paginado
    // - OrderBy...Desc no nome do método gera ORDER BY create_at DESC (mais recente)
    // - Pageable (parametro) informa página e itens por página; Page (retorno) traz itens da página mais o total de páginas, não carrega o extrato inteiro
    // consulta atende pelo índice idx_le_account do V1__init.sql
    Page<LedgerEntry> findByAccountIdOrderByCreatedAtDesc(UUID accountId, Pageable pageable);

    // consulta a mão com @Query quando o método falha
    // JPL usa o nome da CLASSE (LedgerEntry) e dos CAMPOS Java (amountCents) e não nomes da tabela e das colunas SQL
    // sum(...) soma valores; coalesce(x, OL) troca null por 0 (soma de uma conta sem lançamentos é null)
    // ":accountId" é um parâmetro nomeado, liga ao argumento do método que tem o mesmo nome
    // confere o saldo em cache da conta contra a verdade do ledger
    @Query("select coalesce(sum(e.amountCents), OL) from LedgerEntry e where e.account.id = :accountId")
    long sumByAccountId(UUID accountId);
}
