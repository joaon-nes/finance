package com.joao.finance.common;

    // erro de regra de negócio, como saldo insuficiente, valor inválidom etc...
    // estende RunTimeException (exceção não checada), compilador não obriga a declarar
    // throws em cada método, spring faz rollback da transação quando lançada
    // @RestControllerAdvice transformar a exceção em resposta HTTP 4xx

public class BusinessException extends RuntimeException {
    // super(message) repassa a mensagem para classe pai. getMessage() e devolve depois
    public BusinessException (String message) {
        super(message);
    }
}
