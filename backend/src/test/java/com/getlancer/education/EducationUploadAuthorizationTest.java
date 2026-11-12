package com.getlancer.education;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.getlancer.admin.AdminService;
import com.getlancer.commerce.CommerceEducationBinding;
import com.getlancer.commerce.CommerceStorage;
import com.getlancer.hosting.HostingRepository;
import com.getlancer.products.ProductService;
import com.getlancer.publishing.PublishingCapacity;
import com.getlancer.security.Security;
import com.getlancer.shared.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.web.multipart.MultipartFile;

class EducationUploadAuthorizationTest {
  @Test void approvedActorCheckPrecedesReadingOrInspectingSourceBytes() throws Exception {
    var security = mock(Security.class);
    var request = mock(HttpServletRequest.class);
    var storage = mock(CommerceStorage.class);
    var file = mock(MultipartFile.class);
    var manager = mock(PlatformTransactionManager.class);
    var transaction = new SimpleTransactionStatus();
    when(manager.getTransaction(any())).thenReturn(transaction);
    when(file.getSize()).thenReturn(100L);
    when(security.developer(request, true))
        .thenThrow(new ApiError(403, "FORBIDDEN", "An approved builder is required."));
    var service = new EducationService(
        mock(JdbcTemplate.class), security, mock(ProductService.class), mock(PublishingCapacity.class),
        storage, mock(CommerceEducationBinding.class), mock(HostingRepository.class), mock(AdminService.class),
        new ObjectMapper(), manager);

    var error = assertThrows(ApiError.class,
        () -> service.upload(UUID.randomUUID(), file, 1, true, request));
    assertEquals(403, error.status);
    verify(file, never()).getInputStream();
    verify(file, never()).getBytes();
    verifyNoInteractions(storage);
    verify(manager).rollback(transaction);
  }
}
