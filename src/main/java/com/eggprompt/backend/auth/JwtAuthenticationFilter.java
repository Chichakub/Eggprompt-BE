package com.eggprompt.backend.auth;

import com.eggprompt.backend.user.UserRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.List;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtService jwtService;
    private final UserRepository users;
    private final RevokedTokenStore revokedTokens;
    private static final List<String> ROLES = List.of("Super Admin", "Farm Admin", "Farm Staff", "Finance Staff");

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getRequestURI().equals(request.getContextPath() + "/v1/auth/login");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            try {
                if (revokedTokens.isRevoked(token)) throw new IllegalArgumentException("Revoked token");
                Claims claims = jwtService.parseToken(token);
                if (claims.getExpiration() == null) throw new IllegalArgumentException("Missing expiration");
                var user = users.findById(Integer.valueOf(claims.getSubject()))
                        .filter(u -> u.isActive() && u.getDeletedAt() == null && ROLES.contains(u.getUserRole()))
                        .orElseThrow(() -> new IllegalArgumentException("Unavailable user"));
                var authentication = new UsernamePasswordAuthenticationToken(user.getUsername(), null,
                        List.of(new SimpleGrantedAuthority(user.getUserRole())));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (JwtException | IllegalArgumentException exception) {
                SecurityContextHolder.clearContext();
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
