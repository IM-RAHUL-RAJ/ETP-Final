package com.capstone.etp.security;

import com.capstone.etp.exception.AccountAccessDeniedException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

@Slf4j
@Component
public class AccountAuthorizer {

    public void ensureAccountAccess(Long accountId) {
        AuthenticatedUser user = getAuthenticatedUser();
        if (isAdmin(user)) {
            return;
        }

        List<Long> accountIds = user.accountIds();
        if (accountIds == null || !accountIds.contains(accountId)) {
            logDenied(user, accountId);
            throw new AccountAccessDeniedException();
        }
    }


    public void ensureOwnAccount(Long accountId) {
        AuthenticatedUser user = getAuthenticatedUser();
        List<Long> accountIds = user.accountIds();
        if (accountId == null || accountIds == null || !accountIds.contains(accountId)) {
            logDenied(user, accountId);
            throw new AccountAccessDeniedException();
        }
    }

    /**
     * The account a customer is asking about: the one named, which must be one of the token's own, or else the
     * token's first account. A token with no account (an administrator's, say) has none to ask about.
     */
    public Long resolveOwnAccount(Long requested) {
        if (requested != null) {
            ensureOwnAccount(requested);
            return requested;
        }
        AuthenticatedUser user = getAuthenticatedUser();
        List<Long> accountIds = user.accountIds();
        if (accountIds == null || accountIds.isEmpty()) {
            logDenied(user, null);
            throw new AccountAccessDeniedException();
        }
        return accountIds.get(0);
    }

    /** The accounts named in the token. Empty when it names none. */
    public List<Long> ownAccountIds() {
        List<Long> accountIds = getAuthenticatedUser().accountIds();
        return accountIds == null ? List.of() : accountIds;
    }

    private void logDenied(AuthenticatedUser user, Long requestedAccountId) {
        // The subject is the token's own identifier; nothing the caller typed is written to the log.
        log.warn("[ACCESS_DENIED] subject={} requestedAccountId={} tokenAccountIds={}",
                user.subject(), requestedAccountId, user.accountIds());
    }

    /** For endpoints that only an admin may call. */
    public void ensureAdmin() {
        if (!isAdmin(getAuthenticatedUser())) {
            throw new AccountAccessDeniedException("Admin access required");
        }
    }

    public void ensureOrderManagementAllowed() {
        AuthenticatedUser user = getAuthenticatedUser();
        if (isAdmin(user)) {
            throw new AccountAccessDeniedException(
                    "Admin users can only access read endpoints");
        }
    }

    private AuthenticatedUser getAuthenticatedUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedUser user)) {
            throw new UnauthorizedException("Unauthorised");
        }
        return user;
    }

    private boolean isAdmin(AuthenticatedUser user) {
        if (user.roles() == null) {
            return false;
        }
        return user.roles().stream()
                .filter(role -> role != null && !role.isBlank())
                .map(role -> role.trim().toUpperCase(Locale.ROOT))
                .map(role -> role.startsWith("ROLE_") ? role.substring(5) : role)
                .anyMatch("ADMIN"::equals);
    }
}