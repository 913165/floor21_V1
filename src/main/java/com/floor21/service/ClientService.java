package com.floor21.service;

import com.floor21.entity.Client;
import com.floor21.exception.ResourceNotFoundException;
import com.floor21.repository.BuilderRepository;
import com.floor21.repository.ClientRepository;
import com.floor21.security.TenantContext;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ClientService {

    private final ClientRepository clientRepository;
    private final BuilderRepository builderRepository;

    @Transactional(readOnly = true)
    public List<Client> list() {
        return clientRepository.findByBuilder_IdOrderByFirstNameAscLastNameAsc(TenantContext.requireBuilderId());
    }

    @Transactional(readOnly = true)
    public List<Client> search(String q) {
        if (q == null || q.isBlank()) {
            return list();
        }
        return clientRepository.search(TenantContext.requireBuilderId(), q.trim());
    }

    @Transactional(readOnly = true)
    public Client get(UUID id) {
        return clientRepository
                .findByIdAndBuilder_Id(id, TenantContext.requireBuilderId())
                .orElseThrow(() -> new ResourceNotFoundException("Client not found"));
    }

    @Transactional
    public Client save(Client form) {
        UUID builderId = TenantContext.requireBuilderId();
        Client entity;
        if (form.getId() == null) {
            entity = new Client();
            entity.setCreatedAt(Instant.now());
        } else {
            entity =
                    clientRepository
                            .findByIdAndBuilder_Id(form.getId(), builderId)
                            .orElseThrow(() -> new ResourceNotFoundException("Client not found"));
        }
        var builder = builderRepository.findById(builderId).orElseThrow();
        entity.setBuilder(builder);
        entity.setFirstName(form.getFirstName());
        entity.setLastName(form.getLastName());
        entity.setCompanyName(form.getCompanyName());
        entity.setOccupation(form.getOccupation());
        entity.setAddress1(form.getAddress1());
        entity.setAddress2(form.getAddress2());
        entity.setAddress3(form.getAddress3());
        entity.setCity(form.getCity());
        entity.setPhoneOffice(form.getPhoneOffice());
        entity.setPhoneResidence(form.getPhoneResidence());
        entity.setMobile1(form.getMobile1());
        entity.setMobile2(form.getMobile2());
        entity.setEmail1(form.getEmail1());
        entity.setEmail2(form.getEmail2());
        entity.setPanNumber(form.getPanNumber());
        entity.setAadhaarNumber(form.getAadhaarNumber());
        entity.setDob(form.getDob());
        entity.setDateOfMarriage(form.getDateOfMarriage());
        entity.setDndNoCall(form.getDndNoCall());
        entity.setDndOnlyEmail(form.getDndOnlyEmail());
        entity.setCommAddress1(form.getCommAddress1());
        entity.setCommAddress2(form.getCommAddress2());
        entity.setCommAddress3(form.getCommAddress3());
        entity.setCommCity(form.getCommCity());
        entity.setNamePlateInfo(form.getNamePlateInfo());
        entity.setParticulars(form.getParticulars());
        entity.setUpdatedAt(Instant.now());
        return clientRepository.save(entity);
    }
}
