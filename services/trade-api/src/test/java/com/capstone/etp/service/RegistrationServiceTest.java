package com.capstone.etp.service;

import com.capstone.etp.dto.RegistrationRequest;
import com.capstone.etp.dto.RegistrationResponse;
import com.capstone.etp.enums.AccountType;
import com.capstone.etp.enums.Gender;
import com.capstone.etp.enums.RiskProfile;
import com.capstone.etp.exception.AccountAccessDeniedException;
import com.capstone.etp.exception.RegistrationException;
import com.capstone.etp.mapper.AdminMapper;
import com.capstone.etp.security.AccountAuthorizer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RegistrationServiceTest {

    @Mock
    private AdminMapper adminMapper;

    @Mock
    private AccountAuthorizer accountAuthorizer;

    private RegistrationService service;

    @BeforeEach
    void setUp() {
        service = new RegistrationService(adminMapper, accountAuthorizer);
    }

    private RegistrationRequest request(String email) {
        RegistrationRequest request = new RegistrationRequest();
        request.setName("  Priya Menon ");
        request.setEmail(email);
        request.setPhone("9876543210");
        request.setDateOfBirth(LocalDate.of(1990, 4, 12));
        request.setGender(Gender.FEMALE);
        request.setAccountType(AccountType.INDIAN_EQUITY);
        request.setCurrency("INR");
        request.setOpeningBalance(new BigDecimal("50000.00"));
        request.setRiskProfile(RiskProfile.MODERATE);
        return request;
    }

    private void refsAvailable() {
        when(adminMapper.countClientsByEmail(anyString())).thenReturn(0);
        when(adminMapper.nextClientRef()).thenReturn("C000000011");
        when(adminMapper.nextAccountRef()).thenReturn("ACC0000011");
        when(adminMapper.insertClient(anyString(), anyString(), anyString(), any(), any(), any()))
                .thenReturn(11L);
        when(adminMapper.insertAccount(anyString(), anyLong(), anyString(), anyString(), any())).thenReturn(21L);
    }

    @Test
    void usernameIsTheEmailsLocalPart() {
        assertThat(RegistrationService.usernameFor(" Priya.Menon@Example.com ")).isEqualTo("priya.menon");
    }

    @Test
    void createsClientAccountPortfolioAndPreferencesAndReturnsTheAccountIdForTheLogin() {
        refsAvailable();

        RegistrationResponse response = service.register(request("Priya.Menon@Example.com"));

        assertThat(response.getUsername()).isEqualTo("priya.menon");
        assertThat(response.getClientRef()).isEqualTo("C000000011");
        assertThat(response.getAccountRef()).isEqualTo("ACC0000011");
        assertThat(response.getAccountId()).isEqualTo(21L);

        verify(adminMapper).insertClient(eq("C000000011"), eq("Priya Menon"), eq("priya.menon@example.com"),
                eq("9876543210"), eq(LocalDate.of(1990, 4, 12)), eq("FEMALE"));
        verify(adminMapper).insertAccount("ACC0000011", 11L, "INDIAN_EQUITY", "INR", new BigDecimal("50000.00"));
        verify(adminMapper).insertPortfolio(21L, "Primary portfolio");
        // The id counters are moved past the seeded rows before anything is inserted.
        org.mockito.InOrder order = org.mockito.Mockito.inOrder(adminMapper);
        order.verify(adminMapper).lockReferenceAllocation();
        order.verify(adminMapper).syncIdSequences();
        order.verify(adminMapper).insertClient(anyString(), anyString(), anyString(), any(), any(), any());
        verify(adminMapper).insertPreferences(21L, "MODERATE");
    }

    @Test
    void theClientRowCarriesNoCredentialsAtAll() {
        refsAvailable();

        service.register(request("priya.menon@example.com"));

        // clients has no password column: the login lives in the auth schema, created by the auth service.
        verify(adminMapper).insertClient(anyString(), anyString(), anyString(), any(), any(), any());
    }

    @Test
    void aNonAdminCallerIsStoppedBeforeAnythingIsWritten() {
        doThrow(new AccountAccessDeniedException("Admin access required")).when(accountAuthorizer).ensureAdmin();

        assertThatThrownBy(() -> service.register(request("priya.menon@example.com")))
                .isInstanceOf(AccountAccessDeniedException.class);

        verify(adminMapper, never()).insertClient(any(), any(), any(), any(), any(), any());
    }

    @Test
    void aUsernameThatCannotBeUsedIsRejectedWithAClearMessage() {
        assertThatThrownBy(() -> service.register(request("ab@example.com")))
                .isInstanceOf(RegistrationException.class)
                .hasMessageContaining("before the @");
        assertThatThrownBy(() -> service.register(request("a+b.tag@example.com")))
                .isInstanceOf(RegistrationException.class);

        verify(adminMapper, never()).insertClient(any(), any(), any(), any(), any(), any());
    }

    @Test
    void anEmailThatIsAlreadyAClientIsAConflict() {
        when(adminMapper.countClientsByEmail("priya.menon@example.com")).thenReturn(1);

        assertThatThrownBy(() -> service.register(request("priya.menon@example.com")))
                .isInstanceOf(RegistrationException.class)
                .extracting(e -> ((RegistrationException) e).getCatalogueCode())
                .isEqualTo("REG-409");

        verify(adminMapper, never()).insertClient(any(), any(), any(), any(), any(), any());
    }

    @Test
    void undoRemovesAFreshUnusedRegistrationAndItsClient() {
        when(adminMapper.findRecentAccountClient(21L, RegistrationService.UNDO_WINDOW_MINUTES)).thenReturn(11L);
        when(adminMapper.countActivity(21L)).thenReturn(0);
        when(adminMapper.countAccountsOfClient(11L)).thenReturn(0);

        service.undo(21L);

        org.mockito.InOrder order = org.mockito.Mockito.inOrder(adminMapper);
        order.verify(adminMapper).deletePreferences(21L);
        order.verify(adminMapper).deletePortfolio(21L);
        order.verify(adminMapper).deleteAccount(21L);
        order.verify(adminMapper).deleteClient(11L);
        verify(accountAuthorizer).ensureAdmin();
    }

    @Test
    void undoKeepsTheClientWhenItHasAnotherAccount() {
        when(adminMapper.findRecentAccountClient(21L, RegistrationService.UNDO_WINDOW_MINUTES)).thenReturn(11L);
        when(adminMapper.countActivity(21L)).thenReturn(0);
        when(adminMapper.countAccountsOfClient(11L)).thenReturn(1);

        service.undo(21L);

        verify(adminMapper).deleteAccount(21L);
        verify(adminMapper, never()).deleteClient(anyLong());
    }

    @Test
    void undoRefusesAnEstablishedAccountOrOneThatDoesNotExist() {
        when(adminMapper.findRecentAccountClient(5L, RegistrationService.UNDO_WINDOW_MINUTES)).thenReturn(null);

        assertThatThrownBy(() -> service.undo(5L))
                .isInstanceOf(RegistrationException.class)
                .extracting(e -> ((RegistrationException) e).getCatalogueCode())
                .isEqualTo("REG-404");

        verify(adminMapper, never()).deleteAccount(anyLong());
    }

    @Test
    void undoRefusesAnAccountThatHasBeenUsed() {
        when(adminMapper.findRecentAccountClient(21L, RegistrationService.UNDO_WINDOW_MINUTES)).thenReturn(11L);
        when(adminMapper.countActivity(21L)).thenReturn(3);

        assertThatThrownBy(() -> service.undo(21L))
                .isInstanceOf(RegistrationException.class)
                .extracting(e -> ((RegistrationException) e).getCatalogueCode())
                .isEqualTo("REG-409");

        verify(adminMapper, never()).deleteAccount(anyLong());
    }

    @Test
    void aNonAdminCannotUndo() {
        doThrow(new AccountAccessDeniedException("Admin access required")).when(accountAuthorizer).ensureAdmin();

        assertThatThrownBy(() -> service.undo(21L)).isInstanceOf(AccountAccessDeniedException.class);

        verify(adminMapper, never()).deleteAccount(anyLong());
    }
}
