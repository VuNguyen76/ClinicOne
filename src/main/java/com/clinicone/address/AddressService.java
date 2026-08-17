package com.clinicone.address;

import com.clinicone.auth.AuthException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;

@Service
public class AddressService {
    private final RestClient client;

    public AddressService(RestClient.Builder builder,
                          @Value("${app.address.api-root:https://tinhthanhpho.com/api/v1}") String apiRoot) {
        this.client = builder.baseUrl(apiRoot).build();
    }

    @Cacheable(cacheNames = "addresses", key = "'provinces:' + #page + ':' + #limit")
    public List<AddressUnitResponse> provinces(int page, int limit) {
        return fetch("/provinces?page=" + page + "&limit=" + limit);
    }

    @Cacheable(cacheNames = "addresses", key = "'districts:' + #provinceCode + ':' + #page + ':' + #limit")
    public List<AddressUnitResponse> districts(String provinceCode, int page, int limit) {
        return fetch("/provinces/" + normalizeCode(provinceCode) + "/districts?page=" + page + "&limit=" + limit);
    }

    @Cacheable(cacheNames = "addresses", key = "'wards:' + #districtCode + ':' + #page + ':' + #limit")
    public List<AddressUnitResponse> wards(String districtCode, int page, int limit) {
        return fetch("/districts/" + normalizeCode(districtCode) + "/wards?page=" + page + "&limit=" + limit);
    }

    private List<AddressUnitResponse> fetch(String path) {
        try {
            AddressListResponse response = client.get().uri(path).retrieve().body(AddressListResponse.class);
            return response == null || response.data() == null ? List.of() : response.data();
        } catch (RestClientException exception) {
            throw new AuthException(HttpStatus.SERVICE_UNAVAILABLE, "ADDRESS_PROVIDER_UNAVAILABLE",
                    "Không thể tải danh sách địa chỉ lúc này. Vui lòng thử lại sau.");
        }
    }

    private String normalizeCode(String code) {
        if (code == null || !code.matches("[A-Za-z0-9_-]{1,32}")) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "ADDRESS_CODE_INVALID",
                    "Mã địa chỉ không hợp lệ.");
        }
        return code;
    }

    private record AddressListResponse(boolean success, List<AddressUnitResponse> data) {
    }
}
