package com.yojanamitra.api.user;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

/**
 * Spring Security's own {@code User} carries only credentials and authorities.
 * {@code JwtAuthFilter} additionally needs to know when the password last
 * changed, so it can refuse tokens minted before that. Exposing it on the
 * principal keeps the filter to a single database lookup per request.
 */
public class AppUserPrincipal implements UserDetails {

    private final String username;
    private final String password;
    private final Instant passwordChangedAt;

    public AppUserPrincipal(AppUser user) {
        this.username = user.getUsername();
        this.password = user.getPassword();
        this.passwordChangedAt = user.getPasswordChangedAt();
    }

    /** Null for an account whose password has never been reset. */
    public Instant getPasswordChangedAt() {
        return passwordChangedAt;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("USER"));
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return username;
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
}
