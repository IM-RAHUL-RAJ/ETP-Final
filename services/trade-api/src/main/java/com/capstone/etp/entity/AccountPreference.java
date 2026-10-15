package com.capstone.etp.entity;

import com.capstone.etp.enums.RiskProfile;
import lombok.*;
import java.time.Instant;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AccountPreference {

    private Long id;
    private Long accountId;
    private RiskProfile riskProfile;
    private String investmentGoals;
    private List<String> preferredSectors;
    private Instant createdAt;
}