import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { map, Observable, shareReplay } from 'rxjs';

export interface VietnamAddressUnit {
  name: string;
  code: number;
  division_type: string;
  codename: string;
  province_code?: number;
  district_code?: number;
}

interface ProvinceResponse extends VietnamAddressUnit {
  districts?: VietnamAddressUnit[];
}

interface DistrictResponse extends VietnamAddressUnit {
  wards?: VietnamAddressUnit[];
}

@Injectable({ providedIn: 'root' })
export class VietnamAddressService {
  private readonly http = inject(HttpClient);
  private readonly apiRoot = 'https://provinces.open-api.vn/api/v1';
  private readonly provinceRequest = this.http.get<VietnamAddressUnit[]>(`${this.apiRoot}/?depth=1`).pipe(
    shareReplay({ bufferSize: 1, refCount: true }),
  );
  private readonly districtRequests = new Map<string, Observable<VietnamAddressUnit[]>>();
  private readonly wardRequests = new Map<string, Observable<VietnamAddressUnit[]>>();

  getProvinces(): Observable<VietnamAddressUnit[]> {
    return this.provinceRequest;
  }

  getDistricts(provinceCode: string): Observable<VietnamAddressUnit[]> {
    return this.cachedRequest(this.districtRequests, provinceCode, () => this.http
      .get<ProvinceResponse>(`${this.apiRoot}/p/${provinceCode}?depth=2`)
      .pipe(map((province) => province.districts ?? [])));
  }

  getWards(districtCode: string): Observable<VietnamAddressUnit[]> {
    return this.cachedRequest(this.wardRequests, districtCode, () => this.http
      .get<DistrictResponse>(`${this.apiRoot}/d/${districtCode}?depth=2`)
      .pipe(map((district) => district.wards ?? [])));
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
