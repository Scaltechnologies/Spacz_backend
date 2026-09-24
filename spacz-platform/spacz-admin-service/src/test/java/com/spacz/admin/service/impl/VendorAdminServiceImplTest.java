package com.spacz.admin.service.impl;

import com.spacz.admin.client.AuthServiceClient;
import com.spacz.admin.client.StudyHallServiceClient;
import com.spacz.admin.client.dto.AdminVendorDto;
import com.spacz.admin.client.dto.StatusActionDto;
import com.spacz.admin.entity.AuditAction;
import com.spacz.admin.exception.BusinessRuleException;
import com.spacz.admin.security.AuthenticatedUser;
import com.spacz.admin.security.Role;
import com.spacz.admin.service.AuditService;
import com.spacz.admin.support.AdminFixtures;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VendorAdminServiceImplTest {

    private static final AuthenticatedUser ADMIN = new AuthenticatedUser(1L, "admin@spacz.local", Role.ADMIN);

    @Mock
    private StudyHallServiceClient studyHallClient;
    @Mock
    private AuthServiceClient authClient;
    @Mock
    private AuditService auditService;
    @InjectMocks
    private VendorAdminServiceImpl service;

    @Test
    void approveCallsTheOwningServiceThenAudits() {
        when(studyHallClient.changeVendorStatus(eq(42L), any())).thenReturn(AdminFixtures.vendor(42L, "APPROVED"));

        AdminVendorDto result = service.approve(ADMIN, 42L, null);

        assertThat(result.profile().status()).isEqualTo("APPROVED");
        verify(studyHallClient).changeVendorStatus(42L, new StatusActionDto("APPROVE", null));
        verify(auditService).record(eq(ADMIN), eq(AuditAction.VENDOR_APPROVED), eq("VENDOR"), eq(42L), contains("Focus Hall"));
    }

    @Test
    void suspendPassesTheReasonAndAuditsIt() {
        when(studyHallClient.changeVendorStatus(eq(42L), any())).thenReturn(AdminFixtures.vendor(42L, "SUSPENDED"));

        service.suspend(ADMIN, 42L, "Repeated complaints");

        verify(studyHallClient).changeVendorStatus(42L, new StatusActionDto("SUSPEND", "Repeated complaints"));
        verify(auditService).record(eq(ADMIN), eq(AuditAction.VENDOR_SUSPENDED), eq("VENDOR"), eq(42L),
                contains("Repeated complaints"));
    }

    @Test
    void nothingIsAuditedWhenTheOwningServiceRejectsTheTransition() {
        when(studyHallClient.changeVendorStatus(eq(42L), any()))
                .thenThrow(new BusinessRuleException("INVALID_STATUS_TRANSITION", "Cannot approve a vendor in status DRAFT"));

        assertThatThrownBy(() -> service.approve(ADMIN, 42L, null)).isInstanceOf(BusinessRuleException.class);
        verify(auditService, never()).record(any(), any(), anyString(), anyLong(), anyString());
    }
}
