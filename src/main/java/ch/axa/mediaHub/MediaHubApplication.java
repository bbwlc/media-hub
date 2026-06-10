package ch.axa.mediaHub;

import ch.axa.mediaHub.repository.AccountRepository;
import org.apache.catalina.core.ApplicationContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.FilterChainProxy;

@SpringBootApplication
public class MediaHubApplication implements ApplicationRunner {

    @Autowired
    private AccountRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    public static void main(String[] args) {
        SpringApplication.run(MediaHubApplication.class, args);
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        System.out.println(passwordEncoder.encode("admin"));

        userRepository
            .findAll()
            .forEach(user -> {
                System.out.println(
                        user.getUsername() + " - " +
                        user.getPasswordHash());
            });

    }
}
