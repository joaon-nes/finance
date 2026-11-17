package com.joao.finance.account;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

    // dto de entrada: corpo Json do POST /api/accounts ex {"name": "Corrente", "type": "CHECKING"}
    // record imutável, jackson preenche construtor
    // só recebe o que o cliente pode escolher: id, saldo, versão e dono definidos pelo sistema
    // anotações são verificadas porque o controller usa @Valid, seria ignoradas sem
    // violação -> 400 auto do spring (erro detalhado a implementar)
    // tipo inválido no JSON é recuso com 400 antes da validação, ao converter enum 

public record CreateAccountRequest (
        // @NotBlank recusa null, "" e só espaços
    // @Size espelha a coluna VARCHAR(80) do db
    @NotBlank @Size(max = 80) String name,
    @NotNull Account.Type type
) {}
