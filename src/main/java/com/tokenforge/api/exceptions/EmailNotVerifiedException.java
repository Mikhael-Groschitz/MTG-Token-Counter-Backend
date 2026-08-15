package com.tokenforge.api.exceptions;

import lombok.Getter;

@Getter
public class EmailNotVerifiedException extends RuntimeException {
    private final String email;

    public EmailNotVerifiedException(String email) {
        super("Sua conta ainda não foi verificada. Confirme o código enviado ao seu e-mail.");
        this.email = email;
    }
}