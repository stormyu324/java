package com.quant.trading;

import com.quant.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Confirming an order requires typing the login password again, so a browser that is merely left logged in
 * (Basic credentials are cached) cannot confirm trades.
 */
@Component
public class Reauthenticator {

    private static final Logger log = LoggerFactory.getLogger(Reauthenticator.class);

    private final UserDetailsService users;
    private final PasswordEncoder encoder;
    private final AppProperties app;

    public Reauthenticator(UserDetailsService users, PasswordEncoder encoder, AppProperties app) {
        this.users = users;
        this.encoder = encoder;
        this.app = app;
    }

    public void verify(String password) {
        String hash = users.loadUserByUsername(app.username()).getPassword();
        if (password == null || password.isEmpty() || !encoder.matches(password, hash)) {
            log.warn("Order confirmation attempted with a wrong password");
            throw new ConfirmationDeniedException("Wrong password, order not confirmed");
        }
    }

    public static class ConfirmationDeniedException extends RuntimeException {
        public ConfirmationDeniedException(String message) {
            super(message);
        }
    }
}
