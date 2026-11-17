package com.joao.finance.ledger;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

// uma linha por extrato, o lançamento (valor) + dados da transação que ele pertence (tipo, desc, data)
// amountCents positivo: entrada na conta, negativo: saída
// não incluí categoria, conta do outro lado em transferencias e marcação de estorno (reverses_id)

public record StatementEntryResponse (UUID  entryId, UUID transactionId, Transaction.Type type, String description, LocalDate occurredOn, long amountCents, Instant createdAt) {
    // precisa ser chamado dentro de uma transação, getTransaction() é LAZY (no extrato vem carregada pelo @EntityGraph do repository)
    public static StatementEntryResponse from (LedgerEntry entry) {
        Transaction tx = entry.getTransaction();
        return new StatementEntryResponse (
        entry.getId(),
        tx.getId(),
        tx.getType(),
        tx.getDescription(),
        tx.getOccurredOn(),
        entry.getAmountCents(),
        entry.getCreatedAt());
    }
}
