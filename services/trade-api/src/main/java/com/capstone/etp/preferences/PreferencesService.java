package com.capstone.etp.preferences;

import com.capstone.etp.exception.AccountNotFoundException;
import com.capstone.etp.exception.ModuleException;
import com.capstone.etp.security.AccountAuthorizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;


@Slf4j
@Service
@RequiredArgsConstructor

// Manages customer notification preferences and resolves the effective channel for notification delivery.
public class PreferencesService implements ChannelResolver {

    /** The kinds of message a customer can switch on or off. */
    static final List<String> ALL_KINDS = List.of("ORDER_FILLED", "ORDER_REJECTED", "ORDER_CANCELLED", "PRICE_ALERT", "FUNDS");

    static final AlertChannel DEFAULT_CHANNEL = AlertChannel.EMAIL;
    static final AlertChannel DEFAULT_CHANNEL_WITHOUT_EMAIL = AlertChannel.PUSH;

    private final PreferencesMapper mapper;
    private final AccountAuthorizer authorizer;   

    public PreferencesResponse get(Long accountId) {
        authorizer.ensureOwnAccount(accountId);
        return toResponse(accountId, requireRow(accountId));
    }

    @Transactional
    public PreferencesResponse update(Long accountId, UpdatePreferencesRequest request) {
        authorizer.ensureOwnAccount(accountId);
        PreferenceRow row = requireRow(accountId);

        // The default account must be one this sign-in may use, and one of this customer's own.
        authorizer.ensureOwnAccount(request.defaultAccountId());
        if (mapper.countAccountOfClient(request.defaultAccountId(), row.getClientId()) == 0) {
            throw invalid("The default account must be one of your own accounts");
        }

        // A channel that cannot reach the customer is refused now, not discovered at send time.
        if (destinationFor(request.alertChannel(), row, accountId).isEmpty()) {
            throw invalid(request.alertChannel() == AlertChannel.SMS
                    ? "There is no phone number on your profile, so SMS alerts are not available"
                    : "There is no e-mail address on your profile, so e-mail alerts are not available");
        }

        mapper.upsert(row.getClientId(), request.defaultAccountId(), request.alertChannel());
        if (request.hasExtras()) {
            mapper.updateExtras(row.getClientId(), request.mascot(), request.notificationsEnabled(),
                    request.notificationKinds() == null ? null : String.join(",", validKinds(request.notificationKinds())));
        }
        log.info("[PREFERENCES] updated clientId={} defaultAccountId={} channel={}",
                row.getClientId(), request.defaultAccountId(), request.alertChannel());
        return toResponse(accountId, requireRow(accountId));
    }
 

    @Override
    public String mascotOf(long accountId) {
        try {
            PreferenceRow row = mapper.findByAccountId(accountId);
            return row == null || row.getMascot() == null ? Mascot.OWL.name() : row.getMascot().name();
        } catch (RuntimeException e) {
            return Mascot.OWL.name();
        }
    }

    @Override
    public boolean wants(long accountId, String kind) {
        try {
            PreferenceRow row = mapper.findByAccountId(accountId);
            if (row == null) {
                return true;
            }
            if (Boolean.FALSE.equals(row.getNotificationsEnabled())) {
                return false;
            }
            return row.getNotificationKinds() == null || kindsOf(row).contains(kind);
        } catch (RuntimeException e) {
            // An unreadable preference must not silence a message.
            return true;
        }
    }

    @Override
    public Optional<ResolvedChannel> resolve(long accountId) {
        PreferenceRow row;
        try {
            row = mapper.findByAccountId(accountId);
        } catch (RuntimeException e) {
            throw new PreferencesUnavailableException("Preferences could not be read", e);
        }
        if (row == null) {
            return Optional.empty();
        }
        if (row.getAlertChannel() == null) {
            return Optional.of(platformDefault(accountId, row));
        }
        AlertChannel chosen = row.getAlertChannel();
        return destinationFor(chosen, row, accountId)
                .map(destination -> new ResolvedChannel(chosen, destination, ChannelSource.STORED));
    }
   

    private PreferenceRow requireRow(Long accountId) {
        PreferenceRow row = mapper.findByAccountId(accountId);
        if (row == null) {
            throw new AccountNotFoundException(accountId);
        }
        return row;
    }

    private ResolvedChannel platformDefault(long accountId, PreferenceRow row) {
        AlertChannel channel = hasText(row.getEmail()) ? DEFAULT_CHANNEL : DEFAULT_CHANNEL_WITHOUT_EMAIL;
        String destination = destinationFor(channel, row, accountId).orElseThrow();
        return new ResolvedChannel(channel, destination, ChannelSource.DEFAULT);
    }

    /** Where a message goes on a channel, read from the profile. Empty when the profile cannot serve it. */
    private static Optional<String> destinationFor(AlertChannel channel, PreferenceRow row, long accountId) {
        return switch (channel) {
            case EMAIL -> hasText(row.getEmail()) ? Optional.of(row.getEmail().trim()) : Optional.empty();
            case SMS -> hasText(row.getPhone()) ? Optional.of(row.getPhone().trim()) : Optional.empty();
            // The in-app/push channel needs no contact detail: it addresses the account's inbox.
            case PUSH -> Optional.of("account:" + accountId);
        };
    }

    private PreferencesResponse toResponse(Long accountId, PreferenceRow row) {
        boolean stored = row.getAlertChannel() != null;
        AlertChannel effective = stored
                ? row.getAlertChannel()
                : (hasText(row.getEmail()) ? DEFAULT_CHANNEL : DEFAULT_CHANNEL_WITHOUT_EMAIL);

        return new PreferencesResponse(
                accountId,
                row.getDefaultAccountId() != null ? row.getDefaultAccountId() : accountId,
                effective,
                maskedDestination(effective, row),
                stored ? ChannelSource.STORED : ChannelSource.DEFAULT,
                List.of(
                        new PreferencesResponse.ChannelOption(AlertChannel.EMAIL, hasText(row.getEmail()),
                                hasText(row.getEmail()) ? ContactMasker.maskEmail(row.getEmail())
                                        : "No e-mail address on your profile"),
                        new PreferencesResponse.ChannelOption(AlertChannel.SMS, hasText(row.getPhone()),
                                hasText(row.getPhone()) ? ContactMasker.maskPhone(row.getPhone())
                                        : "No phone number on your profile"),
                        new PreferencesResponse.ChannelOption(AlertChannel.PUSH, true, "In-app inbox")),
                row.getUpdatedAt(),
                row.getMascot() == null ? Mascot.OWL : row.getMascot(),
                !Boolean.FALSE.equals(row.getNotificationsEnabled()),
                row.getNotificationKinds() == null ? ALL_KINDS : kindsOf(row));
    }

    private static List<String> kindsOf(PreferenceRow row) {
        String stored = row.getNotificationKinds();
        if (stored == null || stored.isBlank()) {
            return List.of();
        }
        return List.of(stored.split(","));
    }

    /** Only kinds the platform sends are accepted, so a typo cannot silence a real message by accident. */
    private static List<String> validKinds(List<String> requested) {
        List<String> unknown = requested.stream().filter(kind -> !ALL_KINDS.contains(kind)).toList();
        if (!unknown.isEmpty()) {
            throw invalid("Unknown notification type");
        }
        return requested.stream().distinct().toList();
    }

    private static String maskedDestination(AlertChannel channel, PreferenceRow row) {
        return switch (channel) {
            case EMAIL -> ContactMasker.maskEmail(row.getEmail());
            case SMS -> ContactMasker.maskPhone(row.getPhone());
            case PUSH -> "In-app inbox";
        };
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static ModuleException invalid(String message) {
        return new ModuleException(HttpStatus.UNPROCESSABLE_ENTITY, "VAL-422", message);
    }
}