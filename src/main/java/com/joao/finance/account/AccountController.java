package com.joao.finance.account;

import com.joao.finance.user.CurrentUserProvider;
// @Valid aciona o Bean Validation do objeto anotado
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
// @RestController: @Controller + @ResponseBody, retorno dos métodos vira JSON
import org.springframework.web.bind.annotation.*;
// monta url da conta criada a partir da url da requisição atual
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import java.net.URI;
import java.util.List;
import java.util.UUID;

// camada http recebe requisição, descobre usuário, chama o service e converte resposta em dto
@RestController
// prefixo comum para todas rotas da classe
@RequestMapping ("/api/accounts")
public class AccountController {
    private final AccountService accountService;
    private final CurrentUserProvider currentUser;

    public AccountController (AccountService accountService, CurrentUserProvider currentUser) {
        this.accountService = accountService;
        this.currentUser = currentUser;
    }
    // POST /api/accounts
    // @RequestBody converte JSON do corpo no record CreateAccountRequest
    // @Valid valida @NotBlank, @Size, @NotNull antes de entrar no método, se falhar spring responde 400
    // responde 201 Created + header Location com a url da conta nova (padrão REST)
    @PostMapping 
    public ResponseEntity<AccountResponse> create (@Valid @RequestBody CreateAccountRequest request) {
        Account account = accountService.create(currentUser.currentUserId(), request.name(), request.type());
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{accountId}").buildAndExpand(account.getId()).toUri();
        return
        ResponseEntity.created(location).body(AccountResponse.from(account));
    }

    // GET /api/accounts: só contas ativas do usuários
    // stream().map(...).toList() converte cada account em accountresponse
    @GetMapping
    public List<AccountResponse> list () {
        return accountService.listActive(currentUser.currentUserId()).stream().map(AccountResponse::from).toList();
    }

    // GET /api/accounts/{accountId}
    // variável igual usada no extrato (/api/accounts/{accountId}/statement)
    // @PathVariable pega {accountId} da url e texto que não é válido recebe 400 do spring
    @GetMapping ("/{accountId}")
    public AccountResponse get (@PathVariable UUID accountId) {
        return AccountResponse  .from(accountService.get(currentUser.currentUserId(), accountId));
    }

}
