package com.joao.finance.ledger;

import com.joao.finance.user.User;
import jakarta.persistence.*;
import java.time.Instant;

//LocalDate somente data, sem horário > bate com a coluna DATE do DB

import java.time.LocalDate;
import java.util.UUID;

// operação de negócio
// movimentação de dinheiro fica no LedgerEntry, Transaction possuí 1 ou 2 lançamentos.

@Entity
@Table (name = "transactions")

public class Transaction {
    
    //INCOME = receita, EXPENSE = despensa, TRANSFER = entre contas

    public enum Type {INCOME, EXPENSE, TRANSFER}

    @Id
    @GeneratedValue (strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne (fetch = FetchType.LAZY, optional = false)
    @JoinColumn (name = "user_id", nullable = false)
    private User user;

    @Enumerated (EnumType.STRING)
    @Column (nullable = false, length = 10)
    private Type type;

    @Column (nullable = false, length = 200)
    private String description;

    // dia que ocorreu a operação != createdAt que registra o instante
    @Column (name = "occurred_on", nullable = false)
    private LocalDate occurredOn;

    // opcional (null para lançamentos manuais), UNIQUE (user_id, idempotency_key) do banco nega o pedido duplicado com mesma chave
    @Column (name = "idempotency_key", length = 80)
    private String idempotencyKey;

    @Column (name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    // Colunas category_id, important_batch_id e reverses_id, serão mapeadas futuramente conforme implementação das funcionalidades
    protected Transaction() {}

    public Transaction (User user, Type type, String description, LocalDate occuredOn, String idempotencyKey) {
        this.user = user;
        this.type = type;
        this.description = description;
        this.occurredOn = occuredOn;
        this.idempotencyKey = idempotencyKey;
    }

    public UUID getId() {return id;}
    public User getUser() {return user;}
    public Type getType() {return type;}
    public String getDescription() {return description;}
    public LocalDate getOccurredOn() {return occurredOn;}
    public String getIdempotencyKey() {return idempotencyKey;}
    public Instant getCreatedAt() {return createdAt;}


}
