package com.specwatch.config;

import com.specwatch.model.User;
import com.specwatch.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2UserAuthority;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class CustomOAuth2UserService implements OAuth2UserService<OAuth2UserRequest, OAuth2User> {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        DefaultOAuth2UserService delegate = new DefaultOAuth2UserService();
        OAuth2User oAuth2User = delegate.loadUser(userRequest);

        String registrationId = userRequest.getClientRegistration().getRegistrationId();
        Map<String, Object> attributes = oAuth2User.getAttributes();

        String email;
        String name;

        if ("github".equals(registrationId)) {
            email = (String) attributes.get("email");
            name  = (String) attributes.get("name");
            if (name == null || name.isBlank()) {
                name = (String) attributes.get("login");
            }
            if (email == null || email.isBlank()) {
                Object id = attributes.get("id");
                email = id + "+noreply@users.noreply.github.com";
            }
        } else {
            email = (String) attributes.get("email");
            name  = (String) attributes.get("name");
        }

        String finalEmail    = email.toLowerCase().trim();
        String finalName     = (name != null && !name.isBlank()) ? name : finalEmail;
        String provider      = registrationId.toUpperCase();

        userRepository.findByEmail(finalEmail).orElseGet(() -> {
            User newUser = new User();
            newUser.setEmail(finalEmail);
            newUser.setName(finalName);
            newUser.setProvider(provider);
            newUser.setEmailVerified(true);
            newUser.setPassword(passwordEncoder.encode(UUID.randomUUID().toString()));
            log.info("Created new OAuth2 user: {} via {}", finalEmail, provider);
            return userRepository.save(newUser);
        });

        Map<String, Object> enriched = new HashMap<>(attributes);
        enriched.put("email", finalEmail);
        enriched.put("name", finalName);

        String nameAttributeKey = "github".equals(registrationId) ? "login" : "sub";
        return new DefaultOAuth2User(
            Collections.singleton(new OAuth2UserAuthority(enriched)),
            enriched,
            nameAttributeKey
        );
    }
}
