package com.yojanamitra.api.security;

import com.yojanamitra.api.user.AppUserDetailsService;
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
                String username = jwt.extractUsername(header.substring(7));
                UserDetails user = userDetailsService.loadUserByUsername(username);
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
}
