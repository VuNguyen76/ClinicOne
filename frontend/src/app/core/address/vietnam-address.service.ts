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

@Injectable({ providedIn: 'root' })
export class VietnamAddressService {
  private readonly http = inject(HttpClient);
  // Đi qua backend cùng origin để tránh CORS và không phụ thuộc vào việc trình
  // duyệt có cho phép gọi trực tiếp dịch vụ địa chỉ bên thứ ba hay không.
  private readonly apiRoot = '/api/v1/addresses';
  private readonly provinceRequest = this.http.get<VietnamAddressUnit[]>(`${this.apiRoot}/provinces?page=1&limit=100`).pipe(
    map((response) => response ?? []),
    shareReplay({ bufferSize: 1, refCount: true }),
  );
  private readonly districtRequests = new Map<string, Observable<VietnamAddressUnit[]>>();
  private readonly wardRequests = new Map<string, Observable<VietnamAddressUnit[]>>();

  getProvinces(): Observable<VietnamAddressUnit[]> {
    return this.provinceRequest;
  }

  getDistricts(provinceCode: string): Observable<VietnamAddressUnit[]> {
    return this.cachedRequest(this.districtRequests, provinceCode, () => this.http
      .get<VietnamAddressUnit[]>(`${this.apiRoot}/provinces/${provinceCode}/districts?page=1&limit=100`)
      .pipe(map((response) => response ?? [])));
  }

  getWards(districtCode: string): Observable<VietnamAddressUnit[]> {
    return this.cachedRequest(this.wardRequests, districtCode, () => this.http
      .get<VietnamAddressUnit[]>(`${this.apiRoot}/districts/${districtCode}/wards?page=1&limit=100`)
      .pipe(map((response) => response ?? [])));
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
