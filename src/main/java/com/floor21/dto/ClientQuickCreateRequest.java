package com.floor21.dto;

import java.time.LocalDate;

public record ClientQuickCreateRequest(
        String firstName,
        String lastName,
        String companyName,
        String occupation,
        String address1,
        String address2,
        String city,
        String mobile1,
        String mobile2,
        String email1,
        String panNumber,
        String aadhaarNumber,
        LocalDate dob,
        LocalDate dateOfMarriage,
        String particulars) {}
