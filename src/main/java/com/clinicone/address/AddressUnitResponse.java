package com.clinicone.address;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Normalized address unit returned by the same-origin address API. */
public record AddressUnitResponse(
        String name,
        String code,
        String type,
        @JsonProperty("division_type") String divisionType,
        String codename,
        @JsonProperty("province_code") String provinceCode,
        @JsonProperty("district_code") String districtCode) {
}
