import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { BffService } from './bff.service';
import { environment } from '../../environments/environment';

describe('BffService', () => {
  let service: BffService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [HttpClientTestingModule] });
    service = TestBed.inject(BffService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('consulta POST /bff/v1/consulta com numeros', () => {
    let result: unknown;
    service.consultar(['+5511999999999']).subscribe((r) => (result = r));

    const req = httpMock.expectOne(`${environment.bffBaseUrl}/consulta`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ numeros: ['+5511999999999'] });
    req.flush({ total: 1, encontradas: 1, itens: [] });
    expect(result).toBeTruthy();
  });

  it('enviarLote usa multipart FormData', () => {
    const file = new File(['a;b'], 'test.csv', { type: 'text/csv' });
    service.enviarLote(file).subscribe();

    const req = httpMock.expectOne(`${environment.bffBaseUrl}/batch`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body instanceof FormData).toBeTrue();
    req.flush({ jobId: 'abc', status: 'PENDING' });
  });

  it('statusLote GET /batch/{id}', () => {
    service.statusLote('job1').subscribe();
    const req = httpMock.expectOne(`${environment.bffBaseUrl}/batch/job1`);
    expect(req.request.method).toBe('GET');
    req.flush({ status: 'RUNNING' });
  });

  it('urlResultadoLote monta URL de download', () => {
    expect(service.urlResultadoLote('job1')).toBe(`${environment.bffBaseUrl}/batch/job1/resultado`);
  });
});
