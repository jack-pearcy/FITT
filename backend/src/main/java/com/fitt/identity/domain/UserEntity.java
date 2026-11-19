package com.fitt.identity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

@Entity
@Table(name = "users")
public class UserEntity implements UserDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    // Explicitly mapped so Hibernate does not guess the column name.
    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    // Carried by every access token as the "ver" claim. A token is refused when
    // its version is not this one, so adding one ends every session at once.
    @Column(name = "token_version", nullable = false)
    private int tokenVersion;

    // JPA needs this to rebuild objects from query results.
    // Protected so nothing in application code calls it directly.
    protected UserEntity() {}

    public UserEntity(String email, String passwordHash) {
        this.email = email;
        this.passwordHash = passwordHash;
    }

    public Long getId() {
        return id;
    }

    public int getTokenVersion() {
        return tokenVersion;
    }

    // The new hash and the new token version go together: a changed password
    // that left the old sessions alive would not lock anyone out.
    public void changePassword(String newPasswordHash) {
        this.passwordHash = newPasswordHash;
        this.tokenVersion++;
    }

    // Email is the login identifier for FITT so this returns email.
    @Override
    public String getUsername() {
        return email;
    }

    // Returns the BCrypt hash stored in DB.
    // DaoAuthenticationProvider calls matches(rawPassword, getPassword()) at login time.
    @Override
    public String getPassword() {
        return passwordHash;
    }

    // No role system in v1 but Spring Security needs at least one authority.
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_USER"));
    }

    // No account locking or expiration in v1.
    @Override
    public boolean isAccountNonExpired() { return true; }

    @Override
    public boolean isAccountNonLocked() { return true; }

    @Override
    public boolean isCredentialsNonExpired() { return true; }

    @Override
    public boolean isEnabled() { return true; }
}