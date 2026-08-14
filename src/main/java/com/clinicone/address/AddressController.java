package com.clinicone.address;

import lombok.RequiredArgsConstructor;

import com.clinicone.auth.AuthException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/addresses")
public class AddressController {
    private final AddressService service;

    @GetMapping("/provinces")
    public List<AddressUnitResponse> provinces(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "100") int limit) {
        validatePagination(page, limit);
        return service.provinces(page, limit);
    }

    @GetMapping("/provinces/{provinceCode}/districts")
    public List<AddressUnitResponse> districts(
            @PathVariable String provinceCode,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "100") int limit) {
        validateCode(provinceCode);
        validatePagination(page, limit);
        return service.districts(provinceCode, page, limit);
    }

    @GetMapping("/districts/{districtCode}/wards")
    public List<AddressUnitResponse> wards(
            @PathVariable String districtCode,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "100") int limit) {
        validateCode(districtCode);
        validatePagination(page, limit);
        return service.wards(districtCode, page, limit);
    }

    private void validatePagination(int page, int limit) {
        if (page < 1 || limit < 1 || limit > 100) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "ADDRESS_PAGINATION_INVALID",
                    "Tham số phân trang địa chỉ không hợp lệ.");
        }
    }

    private void validateCode(String code) {
        if (code == null || !code.matches("[A-Za-z0-9_-]{1,32}")) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "ADDRESS_CODE_INVALID",
                    "Mã địa chỉ không hợp lệ.");
        }
    }
}
