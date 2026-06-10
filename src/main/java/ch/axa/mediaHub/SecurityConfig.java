package ch.axa.mediaHub;

import ch.axa.mediaHub.jwt.DebugFilter;
import ch.axa.mediaHub.jwt.JWTAuthenticationFilter;
import ch.axa.mediaHub.repository.AccountRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.JdbcUserDetailsManager;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import javax.sql.DataSource;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private final DataSource dataSource;

    public SecurityConfig(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   JWTAuthenticationFilter jwtAuthenticationFilter,
                                                   DebugFilter debugFilter,
                                                   DataSource dataSource) throws Exception {

        http.csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth
                    .requestMatchers("/h2-console/**",
                                     "/signIn", "/signOut",
                                     "/upload/**").permitAll()
                    //.requestMatchers("/upload/**").authenticated()
                    .anyRequest().authenticated()
            )

            // Filter chain:
            // --> JWTAuthenticationFilter
            // --> (UsernamePasswordAuthenticationFilter)
            // --> DebugFilter
            .addFilterBefore(jwtAuthenticationFilter,UsernamePasswordAuthenticationFilter.class)
            .addFilterAfter(debugFilter, UsernamePasswordAuthenticationFilter.class);

       return http.build();
    }

    /*
    @Bean
    public UserDetailsService userDetailsService(AccountRepository accountRepository) {
        return new CustomUserDetailsService(accountRepository);
    }*/

    @Bean
    public UserDetailsService userDetailsService() {
        JdbcUserDetailsManager manager = new JdbcUserDetailsManager(dataSource);
        manager.setUsersByUsernameQuery("SELECT username, password_hash, true FROM account WHERE username = ?");
        manager.setAuthoritiesByUsernameQuery("SELECT username, 'ROLE_USER' FROM account WHERE username = ?");
        return manager;
    }

    @Bean
    public JWTAuthenticationFilter authenticationFilter() {
        return new JWTAuthenticationFilter();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public DebugFilter debugFilter() {
        return new DebugFilter();
    }

}
