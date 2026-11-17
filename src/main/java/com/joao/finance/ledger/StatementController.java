package com.joao.finance.ledger;

import com.joao.finance.common.PageResponse;
import com.joao.finance.user.CurrentUserProvider;
import org.springframework.data.domain.Pageable;
// valores padrão de paginação quando o cliente não manda ?page= e ?size=
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import java.util.UUID;

@RestController 
public class StatementController {
    private final StatementService statementService;
    private final CurrentUserProvider currentUser;

    public StatementController (StatementService statementService, CurrentUserProvider currentUser) {
        this.statementService = statementService;
        this.currentUser = currentUser;
    }
    // GET /api/accounts/{accountId}/statement?page=0&size=20
    // Pageable (não @RequestParamm int size) para spring aplicar spring.data.web.pageable.max-page-size
    // ?page= começa em 0, ?size= acima de 100 é reduzido para 100, ?sort= é ignorado no StatementService
    @GetMapping ("/api/accounts/{accountId}/statement")
    public PageResponse<StatementEntryResponse> statement (@PathVariable UUID accountId, @PageableDefault (size = 20) Pageable pageable) {
        return statementService.statement(currentUser.currentUserId(), accountId, pageable);
    }
}
