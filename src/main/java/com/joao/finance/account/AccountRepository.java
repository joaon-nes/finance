package com.joao.finance.account;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AccountRepository extends JpaRepository<Account, UUID> {

    // And > junta duas condições (WHERE...AND...)
    // True > campo boolean precisa ser true (active = true), sem passar parâmetro
    // UserId atravessa relação, Spring entende como account.user.id para filtrar a coluna user_id sem carregar objeto User
    // List > devolve zero ou mais contas (lista vazia em caso se n ter nenhuma)

    List<Account> findByUserIdAndActiveTrue(UUID userId);

    // busca conta pelço id E pelo dono na mesma consulta
    // sempre usar este em vez de findById em endpoints, se usuário trocar o id na url por o de outro user, nada é encontrado (Optional vazio) em vez de vazar conta alheia
    Optional<Account> findByIdAndUserId(UUID id, UUID userId);
}
