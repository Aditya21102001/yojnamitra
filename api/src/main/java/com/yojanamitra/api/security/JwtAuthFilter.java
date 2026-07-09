package com.yojanamitra.api.security;

import com.yojanamitra.api.user.AppUserDetailsService;
import com.yojanamitra.api.user.AppUserPrincipal;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Reads a Bearer token, validates it, and sets the security context. Invalid or
 * missing tokens simply leave the request anonymous (so public endpoints like
 * /api/match still work, but can see who the caller is when a token is present).
 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwt;
    private final AppUserDetailsService userDetailsService;

    public JwtAuthFilter(JwtService jwt, AppUserDetailsService userDetailsService) {
        this.jwt = jwt;
        this.userDetailsService = userDetailsService;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain chain) throws ServletException, IOException {

        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")
                && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                String token = header.substring(7);
                // ACCESS only: an MFA_CHALLENGE token has a valid signature and the
                // same subject, so without this constraint it would authenticate
                // a caller who never proved the second factor.
                String username = jwt.extractUsername(token, TokenType.ACCESS);
                UserDetails user = userDetailsService.loadUserByUsername(username);

                if (issuedBeforePasswordChange(token, user)) {
                    throw new IllegalStateException("Token predates the current password");
                }

                var authentication = new UsernamePasswordAuthenticationToken(
                        user, null, user.getAuthorities());
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (Exception ex) {
                // Invalid/expired token or unknown user -> stay anonymous.
                SecurityContextHolder.clearContext();
            }
        }
        chain.doFilter(request, response);
    }

    /**
     * A stolen token stays cryptographically valid for its full lifetime, so a
     * password reset has to invalidate it out-of-band. Anything minted before the
     * password changed is refused.
     *
     * <p>{@code iat} has one-second resolution, so a token issued during the same
     * second as the reset is honoured. That one-second overlap is inherent to the
     * JWT claim and is why the comparison is strict rather than inclusive: making
     * it inclusive would reject the fresh token from a user who logs straight back
     * in within the same second.
     */
    private boolean issuedBeforePasswordChange(String token, UserDetails user) {
        if (!(user instanceof AppUserPrincipal principal) || principal.getPasswordChangedAt() == null) {
            return false;
        }
        Instant issuedAt = jwt.extractIssuedAt(token, TokenType.ACCESS);
        Instant changedAt = principal.getPasswordChangedAt().truncatedTo(ChronoUnit.SECONDS);
        return issuedAt.isBefore(changedAt);
    }
}
