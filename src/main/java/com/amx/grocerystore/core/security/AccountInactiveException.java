package com.amx.grocerystore.core.security;

import lombok.Getter;
import org.springframework.security.core.AuthenticationException;

@Getter
public class AccountInactiveException extends AuthenticationException {

    private final String code;

    public AccountInactiveException(String code) {
        super(code);
        this.code = code;
    }

}
