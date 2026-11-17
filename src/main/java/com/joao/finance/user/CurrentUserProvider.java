package com.joao.finance.user;

import org.springframework.stereotype.Component;
import java.util.UUID;

// Temporário: será substituído na etapa Spring Sec + JWT
// centraliza quem é usuário da requisição
// @Component: bean genérico do spring (não é @Service pq não é regra de negócio)

@Component 
public class CurrentUserProvider {
    
    private static final String DEV_EMAIL = "dev@finance.local";
    private static final String DEV_NAME = "Usuário dev";

    // Temporário: password_hard precisa de valor pq é NOT NULL no banco
    // ! não é hash BCrypt válido, nenhuma senha vai bater com ele com JWT entrar e usuário não consegue entrar
    // remover junto com a etapa da autenticação

    private static final String DEV_PASSWORD_HASH = "!";
    private final UserRepository userRepository;

    public CurrentUserProvider (UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    // usuário precisa existir no banco: accounts.user_id tem  chave estrangeira para users.id
    // busca pelo email fixo se não existir (banco dev inicia vazio)
    // id não salva em memória, testes apagam usuários entre cenários, caso salvo apontaria para usuário que não existe mais
    // duas requisições simultaneas tentariam criar o usuário duas vezes e o UNIQUE do email barra a segunda
    public UUID currentUserId () {
        return userRepository.findByEmail(DEV_EMAIL).orElseGet(() -> userRepository.save(new User(DEV_NAME, DEV_EMAIL, DEV_PASSWORD_HASH))).getId();
    }


}
