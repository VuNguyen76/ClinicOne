import { ChangeDetectionStrategy, Component, ElementRef, OnDestroy, ViewChild, inject, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { AccountMenu } from '../../../shared/account-menu/account-menu';

interface DetectedBarcode {
  rawValue: string;
}

interface BarcodeDetectorInstance {
  detect(source: CanvasImageSource): Promise<DetectedBarcode[]>;
}

interface BarcodeDetectorConstructor {
  new(options: { formats: string[] }): BarcodeDetectorInstance;
}

@Component({
  selector: 'app-queue-qr-scanner',
  standalone: true,
  imports: [RouterLink, MatIconModule, AccountMenu],
  templateUrl: './queue-qr-scanner.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class QueueQrScanner implements OnDestroy {
  @ViewChild('camera') private camera?: ElementRef<HTMLVideoElement>;

  private readonly router = inject(Router);
  private stream: MediaStream | null = null;
  private animationFrame: number | null = null;
  protected readonly starting = signal(false);
  protected readonly scanning = signal(false);
  protected readonly error = signal('');

  protected async startCamera(): Promise<void> {
    this.error.set('');
    const Detector = (window as Window & { BarcodeDetector?: BarcodeDetectorConstructor }).BarcodeDetector;
    if (!navigator.mediaDevices?.getUserMedia || !Detector) {
      this.error.set('Trình duyệt chưa hỗ trợ quét QR trực tiếp. Hãy mở ứng dụng Camera của điện thoại để quét mã trước phòng.');
      return;
    }

    this.starting.set(true);
    try {
      this.stream = await navigator.mediaDevices.getUserMedia({
        video: { facingMode: { ideal: 'environment' } },
        audio: false,
      });
      const video = this.camera?.nativeElement;
      if (!video) throw new Error('Camera view is unavailable');
      video.srcObject = this.stream;
      await video.play();
      this.scanning.set(true);
      this.scanFrames(new Detector({ formats: ['qr_code'] }), video);
    } catch {
      this.stopCamera();
      this.error.set('Không thể mở camera. Vui lòng cho phép quyền camera rồi thử lại.');
    } finally {
      this.starting.set(false);
    }
  }

  protected stopCamera(): void {
    if (this.animationFrame !== null) cancelAnimationFrame(this.animationFrame);
    this.animationFrame = null;
    this.stream?.getTracks().forEach((track) => track.stop());
    this.stream = null;
    this.scanning.set(false);
  }

  protected acceptScannedValue(value: string): void {
    try {
      const url = new URL(value, window.location.origin);
      const validPath = /^\/queue\/check-in\/[^/]+$/.test(url.pathname);
      if (url.origin !== window.location.origin || !validPath) {
        this.error.set('Mã QR này không thuộc ClinicOne. Hãy quét đúng mã đặt trước phòng bác sĩ.');
        return;
      }
      this.stopCamera();
      void this.router.navigateByUrl(url.pathname);
    } catch {
      this.error.set('Không đọc được mã QR phòng khám. Vui lòng quét lại.');
    }
  }

  ngOnDestroy(): void {
    this.stopCamera();
  }

  private scanFrames(detector: BarcodeDetectorInstance, video: HTMLVideoElement): void {
    const scan = async (): Promise<void> => {
      if (!this.scanning()) return;
      try {
        const results = await detector.detect(video);
        if (results[0]?.rawValue) {
          this.acceptScannedValue(results[0].rawValue);
          return;
        }
      } catch {
        // A frame may be unreadable while the camera is focusing; keep scanning.
      }
      this.animationFrame = requestAnimationFrame(() => void scan());
    };
    void scan();
  }
}
