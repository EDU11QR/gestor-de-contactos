package com.gestordecontactos.security;

import com.gestordecontactos.entity.Usuario;
import java.io.Serial;
import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

public final class UsuarioPrincipal implements UserDetails, CredentialsContainer, Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private final Long id;
    private final String email;
    private final String nombre;
    private final Collection<GrantedAuthority> authorities;
    private transient String password;

    private UsuarioPrincipal(Long id, String email, String nombre, String password) {
        this.id = id;
        this.email = email;
        this.nombre = nombre;
        this.password = password;
        this.authorities = List.of(new SimpleGrantedAuthority("ROLE_USER"));
    }

    public static UsuarioPrincipal from(Usuario usuario) {
        return new UsuarioPrincipal(usuario.getId(), usuario.getEmail(), usuario.getNombre(),
                usuario.getPasswordHash());
    }

    public Long getId() {
        return id;
    }

    public String getNombre() {
        return nombre;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return Collections.unmodifiableCollection(authorities);
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public void eraseCredentials() {
        this.password = null;
    }
}
