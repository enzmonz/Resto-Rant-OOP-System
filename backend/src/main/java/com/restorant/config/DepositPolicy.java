package com.restorant.config;
import java.math.BigDecimal;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import com.restorant.repository.DepositSettingsRepository;
import com.restorant.repository.BookingMutex;
import org.springframework.transaction.annotation.Transactional;

@Component
public class DepositPolicy {
    private final BigDecimal amount;
    private final String method;
    private final boolean perPerson;
    private final DepositSettingsRepository repository;
    private final BookingMutex mutex;
    public DepositPolicy(@Value("${deposit.amount:0}") BigDecimal amount,@Value("${deposit.method:Contact the restaurant for payment instructions}") String method,
                         @Value("${deposit.per-person:false}") boolean perPerson,DepositSettingsRepository repository,BookingMutex mutex) {
        if(amount.signum()<0 || amount.scale()>2)throw new IllegalArgumentException("Invalid deposit amount");
        this.amount=amount;this.method=method;this.perPerson=perPerson;
        this.repository=repository;
        this.mutex=mutex;
    }
    public BigDecimal getAmount(){var saved=repository.findById(1L).orElseThrow();return saved.getAmount()==null?amount:saved.getAmount();}
    public String getMethod(){var saved=repository.findById(1L).orElseThrow();return saved.getAmount()==null?method:saved.getMethod();}
    public boolean isPerPerson(){var saved=repository.findById(1L).orElseThrow();return saved.getAmount()==null?perPerson:saved.isPerPerson();}
    public BigDecimal total(int guests){return isPerPerson()?getAmount().multiply(BigDecimal.valueOf(guests)):getAmount();}
    public record View(BigDecimal amount,String method,boolean perPerson) {}
    public View snapshot(){var saved=repository.findById(1L).orElseThrow();return saved.getAmount()==null?new View(amount,method,perPerson):new View(saved.getAmount(),saved.getMethod(),saved.isPerPerson());}
    @Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public void configure(BigDecimal amount,String method,boolean perPerson){mutex.acquire();repository.findById(1L).orElseThrow().configure(amount,method,perPerson);}
}
