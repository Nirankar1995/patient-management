package com.pm.authservice.service;

import com.pm.authservice.model.User;
import com.pm.authservice.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class UserService {
    private static final Logger log = LoggerFactory.getLogger(UserService.class);
    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public Optional<User> findByEmail(String email) {
         Optional<User> user = userRepository.findByEmail(email);
         log.info("Found user is {}", user);
         return user;
     }
}
