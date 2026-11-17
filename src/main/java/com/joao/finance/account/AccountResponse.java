package com.joao.finance.account;

import java.time.Instant;
import java.util.UUID;
    // dto de saída: oque a API devolve sobre uma conta a entidade nunca vai direto pro JSON
    // campo user é LAZY, e fora da transação quebraria a serialização
    // expor entidade amarra o formato da API ao formato do banco
    // version (lock otimista) fica fora por enquanto (talvez no futuro vire ETag)
public record AccountResponse (UUID id, String name, Account.Type type, long balanceCents, boolean active, Instant createdAt) {
    // método de fábrica: concentra conversão entidade -> dto em único lugar
    // static chama pela classe (AccountResponse.from(conta)) sem precisar de objeto
    public static AccountResponse from (Account account){
        return new AccountResponse(
            account.getId(),
            account.getName(),
            account.getType(),
            account.getBalanceCents(),
            account.isActive(),
            account.getCreatedAt());
    }
}
