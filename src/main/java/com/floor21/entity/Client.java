package com.floor21.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "clients")
public class Client {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "builder_id", nullable = false)
    private Builder builder;

    @Size(max = 100, message = "First name must be at most 100 characters.")
    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Size(max = 100, message = "Last name must be at most 100 characters.")
    @Column(name = "last_name", length = 100)
    private String lastName;

    @Column(name = "company_name", length = 200)
    private String companyName;

    @Column(length = 100)
    private String occupation;

    @Column(columnDefinition = "TEXT")
    private String address1;

    @Column(columnDefinition = "TEXT")
    private String address2;

    @Column(columnDefinition = "TEXT")
    private String address3;

    @Column(length = 100)
    private String city;

    @Column(name = "phone_office", length = 20)
    private String phoneOffice;

    @Column(name = "phone_residence", length = 20)
    private String phoneResidence;

    @Column(length = 20)
    private String mobile1;

    @Column(length = 20)
    private String mobile2;

    @Column(length = 150)
    private String email1;

    @Column(length = 150)
    private String email2;

    @Column(name = "pan_number", length = 20)
    private String panNumber;

    @Column(name = "aadhaar_number", length = 20)
    private String aadhaarNumber;

    private LocalDate dob;

    @Column(name = "date_of_marriage")
    private LocalDate dateOfMarriage;

    @Column(name = "dnd_no_call")
    private Boolean dndNoCall = false;

    @Column(name = "dnd_only_email")
    private Boolean dndOnlyEmail = false;

    @Column(name = "comm_address1", columnDefinition = "TEXT")
    private String commAddress1;

    @Column(name = "comm_address2", columnDefinition = "TEXT")
    private String commAddress2;

    @Column(name = "comm_address3", columnDefinition = "TEXT")
    private String commAddress3;

    @Column(name = "comm_city", length = 100)
    private String commCity;

    @Column(name = "name_plate_info", length = 300)
    private String namePlateInfo;

    @Column(columnDefinition = "TEXT")
    private String particulars;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    public String displayName() {
        String fn = firstName != null ? firstName.trim() : "";
        String ln = lastName != null ? lastName.trim() : "";
        String combined = (fn + " " + ln).trim();
        if (!combined.isEmpty()) {
            return combined;
        }
        if (mobile1 != null && !mobile1.isBlank()) {
            return mobile1.trim();
        }
        if (email1 != null && !email1.isBlank()) {
            return email1.trim();
        }
        return "Client";
    }
}
