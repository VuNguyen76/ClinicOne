import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { map, Observable, shareReplay } from 'rxjs';

export interface VietnamAddressUnit {
  name: string;
  code: string;
  type?: string;
  division_type?: string;
  codename?: string;
  province_code?: string;
  district_code?: string;
}

interface AddressListResponse {
  success: boolean;
  data: VietnamAddressUnit[];
}

@Injectable({ providedIn: 'root' })
export class VietnamAddressService {
  private readonly http = inject(HttpClient);
  // API này công khai CORS và trả mã dạng chuỗi (giữ được 01, 001, 00001).
  // Dùng API v1 để khớp dữ liệu địa chỉ cũ 63 tỉnh/thành của hồ sơ hiện tại.
  private readonly apiRoot = 'https://tinhthanhpho.com/api/v1';
  private readonly provinceRequest = this.http.get<AddressListResponse>(`${this.apiRoot}/provinces?page=1&limit=100`).pipe(
    map((response) => response.data ?? []),
    shareReplay({ bufferSize: 1, refCount: true }),
  );
  private readonly districtRequests = new Map<string, Observable<VietnamAddressUnit[]>>();
  private readonly wardRequests = new Map<string, Observable<VietnamAddressUnit[]>>();

  getProvinces(): Observable<VietnamAddressUnit[]> {
    return this.provinceRequest;
  }

  getDistricts(provinceCode: string): Observable<VietnamAddressUnit[]> {
    return this.cachedRequest(this.districtRequests, provinceCode, () => this.http
      .get<AddressListResponse>(`${this.apiRoot}/provinces/${provinceCode}/districts?page=1&limit=100`)
      .pipe(map((response) => response.data ?? [])));
  }

  getWards(districtCode: string): Observable<VietnamAddressUnit[]> {
    return this.cachedRequest(this.wardRequests, districtCode, () => this.http
      .get<AddressListResponse>(`${this.apiRoot}/districts/${districtCode}/wards?page=1&limit=100`)
      .pipe(map((response) => response.data ?? [])));
  }

  private cachedRequest(cache: Map<string, Observable<VietnamAddressUnit[]>>, key: string,
                        factory: () => Observable<VietnamAddressUnit[]>): Observable<VietnamAddressUnit[]> {
    const current = cache.get(key);
    if (current) {
      return current;
    }
    const request = factory().pipe(shareReplay({ bufferSize: 1, refCount: true }));
    cache.set(key, request);
    return request;
  }
}
