package com.joao.finance.common;

import org.springframework.data.domain.Page;
import java.util.List;
// formato fixo pra respostas paginadas da API
// serializar o page do spring direto expõe detalhes internos (pageable, sort...) e o próprio spring data avisa que o formato pode mudar entre versões
// com esse record o JSON é controlado por mim
// <T> genérico serve pra qualquer tipo de item (extrato hoje, transações, relatórios públicos...)
public record PageResponse<T> (List<T> content, int page, int size, long totalElements, int totalPages) {
    public static <T> PageResponse<T> from (Page<T> page) {
        return new PageResponse<> (
            page.getContent(),
            page.getNumber(),       // página atual começa em 0
            page.getSize(),         // itens por page
            page.getTotalElements(),
            page.getTotalPages());
    }
}
