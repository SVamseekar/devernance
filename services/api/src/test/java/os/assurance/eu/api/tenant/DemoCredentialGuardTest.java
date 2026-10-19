package os.assurance.eu.api.tenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class DemoCredentialGuardTest {
  private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);

  private UserEntity user(String email, String rawPassword) {
    return new UserEntity(UUID.randomUUID(), UUID.randomUUID(), email, UserRole.ADMIN,
        rawPassword == null ? null : encoder.encode(rawPassword), Instant.now());
  }

  @Test
  void flagsSeededEmailStillUsingDevPassword() {
    UserJpaRepository users = mock(UserJpaRepository.class);
    when(users.findByEmailIgnoreCase("admin@example.com"))
        .thenReturn(Optional.of(user("admin@example.com", DemoCredentialGuard.DEV_PASSWORD)));
    assertThat(DemoCredentialGuard.offendingEmails(users, encoder)).containsExactly("admin@example.com");
  }

  @Test
  void ignoresSeededEmailWithChangedOrNullPassword() {
    UserJpaRepository users = mock(UserJpaRepository.class);
    when(users.findByEmailIgnoreCase("admin@example.com"))
        .thenReturn(Optional.of(user("admin@example.com", "rotated-strong-password")));
    when(users.findByEmailIgnoreCase("compliance@example.com"))
        .thenReturn(Optional.of(user("compliance@example.com", null)));
    assertThat(DemoCredentialGuard.offendingEmails(users, encoder)).isEmpty();
  }

  @Test
  void refusesToStartInPostgresProfileWhenOffendersExist() {
    UserJpaRepository users = mock(UserJpaRepository.class);
    when(users.findByEmailIgnoreCase("legal@example.com"))
        .thenReturn(Optional.of(user("legal@example.com", DemoCredentialGuard.DEV_PASSWORD)));
    MockEnvironment env = new MockEnvironment();
    env.setActiveProfiles("postgres");
    DemoCredentialGuard guard = new DemoCredentialGuard(users, env, false);
    assertThatThrownBy(() -> guard.afterSingletonsInstantiated())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("legal@example.com");
  }

  @Test
  void doesNothingOutsidePostgresProfile() throws Exception {
    UserJpaRepository users = mock(UserJpaRepository.class);
    when(users.findByEmailIgnoreCase("admin@example.com"))
        .thenReturn(Optional.of(user("admin@example.com", DemoCredentialGuard.DEV_PASSWORD)));
    DemoCredentialGuard guard = new DemoCredentialGuard(users, new MockEnvironment(), false);
    guard.afterSingletonsInstantiated();
  }
}
