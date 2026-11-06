package com.joao.finance.account;

//Import de classe de outro pacote
import com.joao.finance.user.User;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity 
@Table (name = "accounts")

public class Account {
    
    public enum Type { CHECKING, SAVINGS, CREDIT_CARD, CASH}

    @Id
    @GeneratedValue (strategy = GenerationType.UUID)
    private UUID id;

    // Relação muitos para um > múltiplas contas de um único usuário
    // no banco a stranger key user_id, objeto user

    // fetch = LAZY, somente carrega o User quando chamar getUser ()
    // @ManyToOne padrão é EAGER (carrega com JOIN) > LAZY explícito
    // optional = false: conta não existe sem usuário (DB = NOT NULL)
    @ManyToOne (fetch = FetchType.LAZY, optional = false)
    // @JoinColumn indica onde guardar a stranger key
    @JoinColumn (name = "user_id", nullable = false)
    private User user;

    @Column (nullable = false, length = 80)
    private  String name;

    @Enumerated (EnumType.STRING)
    @Column (nullable = false, length = 20)
    private Type type;

    // Dinheiro em centavos, em long (nunca null; começa em 0)
    // é cache: a verdade é a soma dos lançamentos ledger
    @Column (name = "balance_cents", nullable = false)
    private long balanceCents;

    // LOCK OTIMISTA. Hibernate faz WHERE version = ? e incrementa valor a cada UPDATE
    // falha a segunda transação com ObjectOptimisticLockingFailureException em caso
    // de duas transações ao mesmo tempo, não sobrescrevendo o saldo silenciosamente
    // Long (wrapper): null = conta nova não salva, hibernate preenche 0 no INSERT
    @Version
    private Long version;

    // desativa a conta em vez de apagar por conta do histórico
    @Column (nullable = false)
    private boolean active = true;

    @Column (name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected Account () {}

    public Account (User user, String name, Type type){
        this.user = user;
        this.name = name;
        this.type = type;
    }

    // A regra de alterar saldo fica dentro da própria entidade
    // Positivo = entrada, negativo = saída
    // não existe setBalanceCents, saldo muda somente aqui
    public void apply (long deltaCents) {
        this.balanceCents += deltaCents;
    } 

    public void deactivate () {this.active = false;}

    public UUID getId() { return id; }
    public User getUser() { return user; }
    public String getName() { return name; }
    public Type getType() { return type; }
    public long getBalanceCents() { return balanceCents; }
    public Long getVersion() { return version; }

    // para boolean o getter segue o padrão is + nome.
    public boolean isActive() { return active; }
    public Instant getCreatedAt() { return createdAt; }
}
