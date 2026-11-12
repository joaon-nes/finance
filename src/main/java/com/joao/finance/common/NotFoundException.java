package com.joao.finance.common;

    // recurso que não existe ou não é do usuário fica separado da BusinessException pq vai virar HTTP 404, enquanto outra vira 422/400

public class NotFoundException extends RuntimeException {
    public NotFoundException (String message) {
        super(message);
    }
}
