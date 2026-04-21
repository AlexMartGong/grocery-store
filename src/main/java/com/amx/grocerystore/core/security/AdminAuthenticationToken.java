package com.amx.grocerystore.core.security;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

public class AdminAuthenticationToken extends AbstractAuthenticationToken {

    private final AdminPrincipal principal;

    public AdminAuthenticationToken(AdminPrincipal principal) {
        super(List.of(new SimpleGrantedAuthority("ROLE_" + principal.role().name())));
        this.principal = principal;
        super.setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        return null;
    }

    @Override
    public AdminPrincipal getPrincipal() {
        return principal;
    }
}
