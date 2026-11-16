package com.joao.finance.ledger;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.EntityGraph;
import java.util.UUID;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, UUID>{
    // @EntityGraph carrega a Transaction de cada lançamento no MESMO select (JOIN) sem isso,
    // o extrato faria 1 select dos lançamentos + 1 select por lançamento para buscar a Transaction (problema N+1)
    // a consulta de contagem (total de páginas) não é afetada
    @EntityGraph(attributePaths = "transaction")
    // extrato paginado
    // - OrderBy...Desc no nome do método gera ORDER BY create_at DESC (mais recente)
    // - Pageable (parametro) informa página e itens por página; Page (retorno) traz itens da página mais o total de páginas, não carrega o extrato inteiro
    // consulta atende pelo índice idx_le_account do V1__init.sql
    Page<LedgerEntry> findByAccountIdOrderByCreatedAtDesc(UUID accountId, Pageable pageable);

    // consulta a mão com @Query quando o método falha
    // JPL usa o nome da CLASSE (LedgerEntry) e dos CAMPOS Java (amountCents) e não nomes da tabela e das colunas SQL
    // sum(...) soma valores; coalesce(x, 0L) troca null por 0 (soma de uma conta sem lançamentos é null)
    // ":accountId" é um parâmetro nomeado, liga ao argumento do método que tem o mesmo nome
    // confere o saldo em cache da conta contra a verdade do ledger
    @Query("select coalesce(sum(e.amountCents), 0L) from LedgerEntry e where e.account.id = :accountId")
    long sumByAccountId(UUID accountId);
}
