package com.capstone.etp.entity;

import com.capstone.etp.enums.ClientStatus;
import com.capstone.etp.enums.Gender;
import lombok.*;
import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Client {
    private Long id;
    private String clientRef;
    private String name;
    private String email;
    private String phone;
    private LocalDate dateOfBirth;
    private Gender gender;
    private ClientStatus status;
}