package com.gestordecontactos.security;

import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;

public final class ErasingDaoAuthenticationProvider extends DaoAuthenticationProvider {

    public ErasingDaoAuthenticationProvider(UserDetailsService userDetailsService) {
        super(userDetailsService);
    }

    @Override
    protected Authentication createSuccessAuthentication(Object principal, Authentication authentication,
            UserDetails user) {
        Authentication result = super.createSuccessAuthentication(principal, authentication, user);
        if (principal instanceof CredentialsContainer credentialsContainer) {
            credentialsContainer.eraseCredentials();
        }
        return result;
    }
}
