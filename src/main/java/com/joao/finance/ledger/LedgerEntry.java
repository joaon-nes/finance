package com.joao.finance.ledger;

import com.joao.finance.account.Account;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

// movimento de dinheiro de uma conta
// Transaction de receita ou gasto tem 1 lançamento, uma transf tem 2 (negativo na origem e positivo no destino), soma sempre = 0
// APPEND-ONLY, lançamentos nunca são alterados, sempre é criada uma nova transação p corrigir

@Entity
@Table(name = "ledger_entries")

public class LedgerEntry {
    @Id
    @GeneratedValue (strategy = GenerationType.UUID)
    private UUID id;

    //updatable = false para todas colunas para nenhum campo ser alterado
    // hibernate reforça imutabilidade do ledger
    @ManyToOne (fetch = FetchType.LAZY, optional = false)
    @JoinColumn (name = "transaction_id" , nullable = false, updatable = false)
    private Transaction transaction;

    @ManyToOne (fetch = FetchType.LAZY, optional = false)
    @JoinColumn (name = "account_id" , nullable = false, updatable = false)
    private Account account;

    // positivo = entrada de dinheiro, negativo = saída, nunca zero
    @Column (name = "amount_cents", nullable = false, updatable = false)
    private long amountCents;

    // para lançamentos vindos da importação de extrato; identifica o arquivo
    // reimportar o mesmo extrato não duplica (UNIQUE conta + hash)
    // null para lançamentos manuais
    @Column (name = "external_hash", length = 64, updatable = false)
    private String externalHash;

    @Column (name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected LedgerEntry () {}

    public LedgerEntry (Transaction transaction, Account account, long amountCents) {
        // valida no construtor, objeto inválido não existe.
        // espelha CHECK (amount_cents <> 0) do banco, mas falha com uma mensagem
        if (amountCents == 0) {
            throw new IllegalArgumentException("Lançamento de valor zero não é permitido");
        }
        this.transaction = transaction;
        this.account = account;
        this.amountCents = amountCents;
    }

    public UUID getId() {return id;}
    public Transaction geTransaction () {return transaction;}
    public Account getAccount () {return account;}
    public long getAmountCents() {return amountCents;}
    public String getExternalHash() {return externalHash;}
    public Instant getCreatedAt() {return createdAt;}

}
