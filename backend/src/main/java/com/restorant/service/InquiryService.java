package com.restorant.service;
import com.restorant.dto.Api;
import com.restorant.entity.Inquiry;
import com.restorant.repository.InquiryRepository;
import com.restorant.exception.ApiException;
import java.time.Clock;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InquiryService {
    private final InquiryRepository repository;private final Clock clock;
    public InquiryService(InquiryRepository repository,Clock clock){this.repository=repository;this.clock=clock;}
    @Transactional public Long submit(Api.InquiryInput input){return repository.save(new Inquiry(input,clock.instant())).getId();}
    public List<Inquiry> list(){return repository.findTop200ByOrderByCreatedAtDesc();}
    @Transactional public void resolve(Long id){repository.findById(id).orElseThrow(()->new ApiException(404,"Inquiry not found")).resolve();}
}
