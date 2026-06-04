package com.floor21.service;

import com.floor21.dto.ClientBuildingNavDto;
import com.floor21.entity.Booking;
import com.floor21.entity.Client;
import com.floor21.entity.Flat;
import com.floor21.exception.ResourceNotFoundException;
import com.floor21.repository.BookingRepository;
import com.floor21.repository.BuilderRepository;
import com.floor21.repository.ClientRepository;
import com.floor21.security.TenantContext;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ClientService {

    public static final int CLIENTS_DEFAULT_PAGE_SIZE = 25;
    public static final int CLIENTS_MAX_PAGE_SIZE = 100;

    private final ClientRepository clientRepository;
    private final BuilderRepository builderRepository;
    private final BookingRepository bookingRepository;

    @Transactional(readOnly = true)
    public Page<Client> listPage(int page, int size, String q, UUID projectId) {
        int safeSize = Math.min(Math.max(size, 5), CLIENTS_MAX_PAGE_SIZE);
        int safePage = Math.max(page, 0);
        String term = q != null && !q.isBlank() ? q.trim() : null;

        UUID builderId = TenantContext.getBuilderIdOrNull();
        if (builderId != null) {
            Pageable pageable = PageRequest.of(safePage, safeSize, tenantSort());
            if (term != null) {
                return clientRepository.search(builderId, term, pageable);
            }
            return clientRepository.findByBuilder_IdOrderByFirstNameAscLastNameAsc(builderId, pageable);
        }
        if (projectId != null) {
            requireTenantProject(projectId);
            Pageable pageable = PageRequest.of(safePage, safeSize, tenantSort());
            if (term != null) {
                return clientRepository.search(projectId, term, pageable);
            }
            return clientRepository.findByBuilder_IdOrderByFirstNameAscLastNameAsc(projectId, pageable);
        }
        Pageable pageable = PageRequest.of(safePage, safeSize, platformSort());
        if (term != null) {
            return clientRepository.searchAllForPlatformAdmin(term, pageable);
        }
        return clientRepository.findAllForPlatformAdmin(pageable);
    }

    /** Full tenant client list for dropdowns (e.g. booking form). */
    @Transactional(readOnly = true)
    public List<Client> list() {
        UUID builderId = TenantContext.requireBuilderId();
        return clientRepository
                .findByBuilder_IdOrderByFirstNameAscLastNameAsc(builderId, Pageable.unpaged())
                .getContent();
    }

    private static Sort tenantSort() {
        return Sort.by(Sort.Order.asc("firstName"), Sort.Order.asc("lastName"));
    }

    private static Sort platformSort() {
        return Sort.by(
                Sort.Order.asc("builder.companyName").ignoreCase(),
                Sort.Order.asc("firstName"),
                Sort.Order.asc("lastName"));
    }

    @Transactional(readOnly = true)
    public Client get(UUID id) {
        UUID tenantId = TenantContext.getBuilderIdOrNull();
        if (tenantId != null) {
            return clientRepository
                    .findByIdAndBuilder_Id(id, tenantId)
                    .orElseThrow(() -> new ResourceNotFoundException("Client not found"));
        }
        return clientRepository
                .findByIdWithBuilder(id)
                .filter(c -> c.getBuilder() != null && !c.getBuilder().isPlatformAdmin())
                .orElseThrow(() -> new ResourceNotFoundException("Client not found"));
    }

    /**
     * Distinct buildings where this client has an active booking, with one flat id per building for deep-linking
     * to the chart.
     */
    @Transactional(readOnly = true)
    public List<ClientBuildingNavDto> listBuildingsForActiveBookings(UUID clientId) {
        Client client = get(clientId);
        UUID builderId = client.getBuilder().getId();
        List<Booking> bookings =
                bookingRepository.findActiveByClientWithFlatAndBuilding(builderId, clientId);
        Map<UUID, ClientBuildingNavDto> byBuilding = new LinkedHashMap<>();
        for (Booking b : bookings) {
            Flat flat = b.getFlat();
            if (flat == null || flat.getBuilding() == null) {
                continue;
            }
            var building = flat.getBuilding();
            UUID bid = building.getId();
            byBuilding.putIfAbsent(
                    bid,
                    new ClientBuildingNavDto(bid, building.getBuildingName(), flat.getId()));
        }
        return new ArrayList<>(byBuilding.values());
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
        entity.setCommAddress1(form.getCommAddress1());
        entity.setCommAddress2(form.getCommAddress2());
        entity.setCommAddress3(form.getCommAddress3());
        entity.setCommCity(form.getCommCity());
        entity.setNamePlateInfo(form.getNamePlateInfo());
        entity.setParticulars(form.getParticulars());
        entity.setUpdatedAt(Instant.now());
        return clientRepository.save(entity);
    }

    private void requireTenantProject(UUID projectId) {
        builderRepository
                .findById(projectId)
                .filter(b -> !b.isPlatformAdmin())
                .orElseThrow(() -> new ResourceNotFoundException("Project not found"));
    }
}
