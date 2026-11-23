package com.eggprompt.backend.auth;

import com.eggprompt.backend.user.*;
import com.eggprompt.backend.exception.ApiException;
import org.junit.jupiter.api.*;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.time.Instant;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EmailVerificationTests {
 @Test void verificationIsSingleUseAndRejectsExpiredLink() {
  var users=mock(UserRepository.class);var generator=new PasswordResetTokenGenerator();
  var service=new EmailVerificationService(users,generator,mock(JavaMailSender.class));
  var user=new User();user.setUserId(5);user.setActive(false);String token=generator.generate(),hash=generator.hash(token);
  user.setEmailVerificationHash(hash);user.setEmailVerificationExpiresAt(Instant.now().minusSeconds(1));
  when(users.findVerificationUserId(hash)).thenReturn(Optional.of(5));when(users.findByIdForUpdate(5)).thenReturn(Optional.of(user));
  assertThrows(ApiException.class,()->service.verify(token));
  user.setEmailVerificationExpiresAt(Instant.now().plusSeconds(600));service.verify(token);
  assertTrue(user.isActive());assertNull(user.getEmailVerificationHash());
  assertThrows(ApiException.class,()->service.verify(token));
 }
 @Test void projectSenderSendsOnlyAfterCommitAndStoresOnlyTokenHash() {
  var users=mock(UserRepository.class);var sender=mock(JavaMailSender.class);var generator=new PasswordResetTokenGenerator();
  var service=new EmailVerificationService(users,generator,sender);
  ReflectionTestUtils.setField(service,"from","project@example.com");ReflectionTestUtils.setField(service,"verificationUrl","https://example.com/verify-email");
  var user=new User();user.setEmail("worker@example.com");TransactionSynchronizationManager.initSynchronization();
  try{service.issue(user);verifyNoInteractions(sender);
   var callbacks=TransactionSynchronizationManager.getSynchronizations();assertEquals(1,callbacks.size());callbacks.getFirst().afterCommit();
   var mail=org.mockito.ArgumentCaptor.forClass(org.springframework.mail.SimpleMailMessage.class);verify(sender).send(mail.capture());
   assertEquals("project@example.com",mail.getValue().getFrom());assertEquals(64,user.getEmailVerificationHash().length());
   assertFalse(mail.getValue().getText().contains(user.getEmailVerificationHash()));
  }finally{TransactionSynchronizationManager.clearSynchronization();}
 }
}
