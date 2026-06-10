package ch.axa.mediaHub.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@Entity
@ToString
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String username;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(nullable = true)
    private String email;

    @Column(nullable = false)
    private String role = "ROLE_USER";

    @Column(nullable = true /* TODO: unique = true */)
    private String token;

    @Column(name = "profile_picture")
    private String profilePicture;
}