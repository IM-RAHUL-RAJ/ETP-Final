package com.capstone.etp.service;

import com.capstone.etp.dto.AdminAccountSummary;
import com.capstone.etp.dto.RegistrationRequest;
import com.capstone.etp.dto.RegistrationResponse;
import com.capstone.etp.exception.RegistrationException;
import com.capstone.etp.mapper.AdminMapper;
import com.capstone.etp.security.AccountAuthorizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Admin-only. Opens a client, an account and its portfolio in one transaction. The login (and the
 * emailed default password) is created afterwards by the admin's browser calling the auth service,
 * using the account id returned here. If that step fails, the browser asks for {@link #undo}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RegistrationService {

    private static final Pattern USERNAME = Pattern.compile("^[a-z0-9._-]{3,64}$");

    /** An undo only reaches accounts this young, so it can never remove a real, established account. */
    static final int UNDO_WINDOW_MINUTES = 15;

    private final AdminMapper adminMapper;
    private final AccountAuthorizer accountAuthorizer;

    /** Every account with its client, for the admin dashboard. Read only. */
    public List<AdminAccountSummary> listAccounts() {
        accountAuthorizer.ensureAdmin();
        return adminMapper.listAccounts();
    }

    /** The username is the email's local part: priya.menon@example.com becomes priya.menon. */
    static String usernameFor(String email) {
        return email.trim().toLowerCase(Locale.ROOT).split("@", 2)[0];
    }

    @Transactional
    public RegistrationResponse register(RegistrationRequest request) {
        accountAuthorizer.ensureAdmin();

        String email = request.getEmail().trim().toLowerCase(Locale.ROOT);
        String username = usernameFor(email);
        if (!USERNAME.matcher(username).matches()) {
            throw new RegistrationException("REG-422", HttpStatus.UNPROCESSABLE_ENTITY,
                    "The part of the email before the @ becomes the username, so it must be 3 to 64 letters, "
                            + "numbers, dots, underscores or hyphens");
        }

        adminMapper.lockReferenceAllocation();
        adminMapper.syncIdSequences();
        if (adminMapper.countClientsByEmail(email) > 0) {
            throw new RegistrationException("REG-409", HttpStatus.CONFLICT,
                    "That username or email is already registered");
        }

        String clientRef = adminMapper.nextClientRef();
        String accountRef = adminMapper.nextAccountRef();

        // The client row holds no credentials: sign-in is the auth service's job (schema auth).
        Long clientId = adminMapper.insertClient(
                clientRef,
                request.getName().trim(),
                email,
                request.getPhone(),
                request.getDateOfBirth(),
                request.getGender() == null ? null : request.getGender().name());

        BigDecimal opening = request.getOpeningBalance() == null ? BigDecimal.ZERO : request.getOpeningBalance();
        Long accountId = adminMapper.insertAccount(
                accountRef, clientId, request.getAccountType().name(), request.getCurrency(), opening);
        adminMapper.insertPortfolio(accountId, "Primary portfolio");
        if (request.getRiskProfile() != null) {
            adminMapper.insertPreferences(accountId, request.getRiskProfile().name());
        }

        log.info("[REGISTRATION] created clientRef={} accountRef={} username={}", clientRef, accountRef, username);
        return RegistrationResponse.builder()
                .clientId(clientId)
                .clientRef(clientRef)
                .accountId(accountId)
                .accountRef(accountRef)
                .username(username)
                .email(email)
                .build();
    }

    /**
     * Removes a registration whose login could not be created. It only acts on an account that is
     * brand new and has never been used, and it removes the client too only if that was its sole account.
     */
    @Transactional
    public void undo(Long accountId) {
        accountAuthorizer.ensureAdmin();

        Long clientId = adminMapper.findRecentAccountClient(accountId, UNDO_WINDOW_MINUTES);
        if (clientId == null) {
            throw new RegistrationException("REG-404", HttpStatus.NOT_FOUND,
                    "There is no account created in the last " + UNDO_WINDOW_MINUTES + " minutes to undo");
        }
        if (adminMapper.countActivity(accountId) > 0) {
            throw new RegistrationException("REG-409", HttpStatus.CONFLICT,
                    "This account has already been used, so it cannot be undone");
        }

        adminMapper.deletePreferences(accountId);
        adminMapper.deletePortfolio(accountId);
        adminMapper.deleteAccount(accountId);
        if (adminMapper.countAccountsOfClient(clientId) == 0) {
            adminMapper.deleteClient(clientId);
        }
        log.info("[REGISTRATION] undone accountId={} clientId={}", accountId, clientId);
    }
}
