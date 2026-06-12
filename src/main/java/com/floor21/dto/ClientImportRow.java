package com.floor21.dto;

import java.time.LocalDate;

public record ClientImportRow(
        String firstName,
        String lastName,
        String companyName,
        String occupation,
        String address1,
        String address2,
        String address3,
        String city,
        String phoneOffice,
        String phoneResidence,
        String mobile1,
        String mobile2,
        String email1,
        String email2,
        String panNumber,
        String aadhaarNumber,
        LocalDate dob,
        LocalDate dateOfMarriage,
        String particulars) {}
