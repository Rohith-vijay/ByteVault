package com.marketplace.auth.security;

import com.marketplace.auth.repository.AuthCredentialsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final AuthCredentialsRepository credentialsRepository;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return credentialsRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User credentials not found for: " + username));
    }
}
