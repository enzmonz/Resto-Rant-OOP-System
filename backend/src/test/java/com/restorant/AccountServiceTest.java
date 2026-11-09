package com.restorant;
import com.restorant.dto.Api;
import com.restorant.repository.UserRepository;
import com.restorant.service.AccountService;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

class AccountServiceTest {
    @Test void mismatchedPasswordDoesNotHashOrPersistAnything() {
        var repository=mock(UserRepository.class);var encoder=mock(PasswordEncoder.class);
        var service=new AccountService(repository,encoder);
        assertThatThrownBy(()->service.register(new Api.Register("Alex","alex@example.com","09171234567","long-password","different-password"))).hasMessage("Passwords do not match");
        verifyNoInteractions(repository,encoder);
    }
}
