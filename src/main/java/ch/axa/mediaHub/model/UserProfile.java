package ch.axa.mediaHub.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "user_profile")
public class UserProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = true)
    private String email;

    @Column(name = "profile_picture")
    private String profilePicture;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private ProfileStatus status = ProfileStatus.UNVERIFIED;

    @JsonIgnore
    @OneToOne
    @JoinColumn(name = "account_id", nullable = false, unique = true)
    private Account account;
}