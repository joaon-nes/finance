package com.joao.finance.ledger;

import com.joao.finance.account.AccountService;
import com.joao.finance.common.PageResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest; 
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

// consulta de extrato (somente leitura) fica no pacote ledger pq lê lançamentos
// usa AccountService pra conferir a posse da conta
@Service
public class StatementService {
    private final AccountService accountService;
    private final LedgerEntryRepository ledgerEntryRepository;

    public StatementService (AccountService accountService, LedgerEntryRepository ledgerEntryRepository) {
        this.accountService = accountService;
        this.ledgerEntryRepository = ledgerEntryRepository;
    }
    // conversão para dto acontece dentro da transação com spring.jpa.open-in-view=false, acessar dados LAZY depois que o método termina daria LazyInitializationException
    @Transactional (readOnly = true)
    public PageResponse<StatementEntryResponse> statement (UUID userId, UUID accountId, Pageable pageable) {
        // conta inexistente ou de outro usuário > NotFoundException
        // conta desativada continua exibindo extrato (histórico não some)
        accountService.get(userId, accountId);
        // aproveita página e tamanho e descarta o sort vindo do cliente (?sort=...)
        // sorte seria somado ao OrderBy do nome do método com propriedade inexistente, consulta falharia 500
        // ordem do extrato é fixa, mais recente primeiro
        // limite de tamanho (max-page-size) já aplicado pelo spring quando montado o Pageable no controller
        Pageable pageOnly = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
        // Page.map converte cada LedgerEntry em StatementEntryResponde mantendo dados de paginação
        Page<StatementEntryResponse> page = ledgerEntryRepository.findByAccountIdOrderByCreatedAtDesc(accountId, pageOnly).map(StatementEntryResponse::from);
        return PageResponse.from(page);
    }

    //implementar filtros por período (de/até occurredOn) e por categoria (incluir na etapa dos relatórios)
}
