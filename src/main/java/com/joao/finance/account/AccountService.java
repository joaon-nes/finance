package com.joao.finance.account;

import com.joao.finance.common.BusinessException;
import com.joao.finance.common.NotFoundException;
import com.joao.finance.user.User;
import com.joao.finance.user.UserRepository;
import org.springframework.stereotype.Service;
// @Transactional do spring (não jakarta) aceita readyOnly
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.UUID;

    // regras de negócio de contas: controller só traduz HTTP <-> Java e chama este service
@Service
public class AccountService {
    private final AccountRepository accountRepository;
    private final UserRepository userRepository;

    public AccountService (AccountRepository accountRepository, UserRepository userRepository) {
        this.accountRepository = accountRepository;
        this.userRepository = userRepository;
    }

    // @Transactional funciona direto (diferente de TransferService) pq não há retry
    // uma tentativa única, COMMIT no fim ou ROLLBACK para exceção
    // controller chama o método fora da classe, chamada passa pelo proxy do spring
    @Transactional
    public Account create (UUID userId, String name, Account.Type type) {
        // pela API o @Valid do controller barra esses casos com 400 antes de chegar aqui
        // checagem fica como recurso pra quando o service for chamado de outro local (testes, recorrência...)
        if (name == null || name.isBlank()) {
            throw new BusinessException("O nome da conta é obrigatório");
        }
        if (type == null) {
            throw new BusinessException("O tipo da conta é obrigatório");
        }
        // trim evita "Corrente" e "Corrente" como contas diferentes
        String trimmedName = name.trim();
        // checagem antes do INSERT para mensagem cclara, quem garante é o UNIQUE (user_id, name) do db
        // duas req simultaneas com mesmo nome podem passar, a segunda falha no banco e será tratada como 409
        if (accountRepository.existsByUserIdAndName(userId, trimmedName)) {
            throw new BusinessException("Já existe uma conta com esse nome");
        }
        // getReferenceById: só o id do usuário é necessário para a chave estrangeira
        User user = userRepository.getReferenceById(userId);
        // conta sempre inicia com saldo 0, entra como lançamento de receita, nunca setado direto, cache e ledger não iriam bater
        return accountRepository.save(new Account(user, trimmedName, type));
    }
        // readyOnly true só leitura, hibernate dispensa dirty checking ao final da transação
        @Transactional (readOnly = true)
        public List<Account> listActive (UUID userId) {
            return accountRepository.findByUserIdAndActiveTrue(userId);
        }

        // busca pelo id e pelo dono, conta de outro usuário retorna não encontrada
        // usado pelo StatementService para conferir a posse antes de mostrar o extrato
        @Transactional (readOnly = true)
        public Account get (UUID userId, UUID accountId) {
            return accountRepository.findByIdAndUserId(accountId, userId).orElseThrow(() -> new NotFoundException("Conta não encontrada"));
        }
    }
