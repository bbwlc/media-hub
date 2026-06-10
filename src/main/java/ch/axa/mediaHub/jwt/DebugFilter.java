package ch.axa.mediaHub.jwt;

import ch.axa.mediaHub.SecurityConfig;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

public class DebugFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        var auth = SecurityContextHolder.getContext().getAuthentication();

        System.out.println("DebugFilter: Authenticated = " + (auth != null));
        if (auth != null) {
            System.out.println("--> DebugFilter: Principal = " + auth.getName());
            System.out.println("--> DebugFilter: Authorities = " + auth.getAuthorities());
        }
        filterChain.doFilter(request, response);
    }
}
