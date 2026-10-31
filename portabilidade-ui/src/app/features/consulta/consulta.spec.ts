import { Component, OnInit } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { ConsultaComponent } from './consulta';

/** Wrapper que dispara consultar() automaticamente com entrada pré-definida. */
@Component({ selector: 'x', template: '' })
class Wrapper extends ConsultaComponent implements OnInit {
  override ngOnInit(): void {
    this.entrada = '+5511999999999, +5521988887777';
    this.consultar();
  }
}

describe('ConsultaComponent', () => {
  let httpMock: HttpTestingController;

  beforeEach(() => {
    localStorage.removeItem('ultima-consulta');
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    httpMock = TestBed.inject(HttpTestingController);
  });

  it('parseia múltiplos números separados por vírgula e persiste para dashboard', async () => {
    const fixture = TestBed.createComponent(Wrapper);
    const comp = fixture.componentInstance;
    fixture.detectChanges();

    const req = httpMock.expectOne((u) => u.endsWith('/consulta'));
    expect(req.request.body.numeros).toEqual(['+5511999999999', '+5521988887777']);
    req.flush({
      total: 2,
      encontradas: 1,
      itens: [
        { numero: '+5511999999999', canonical: '5511999999999', operadora: 'CLARO', fonte: 'RN0', mascarado: 'CLA…', encontrada: true },
        { numero: '+5521988887777', canonical: '5521988887777', operadora: null, fonte: null, mascarado: null, encontrada: false },
      ],
    });
    await fixture.whenStable();

    expect(comp.result()).toEqual({ total: 2, encontradas: 1 });
    expect(comp.itens().length).toBe(2);
    const stored = JSON.parse(localStorage.getItem('ultima-consulta')!);
    expect(stored.length).toBe(2);
    expect(stored[0].operadora).toBe('CLARO');
  });

  it('valida entrada vazia sem chamar o BFF', async () => {
    const fixture = TestBed.createComponent(ConsultaComponent);
    const comp = fixture.componentInstance;
    fixture.detectChanges();
    comp.consultar();
    expect(comp.erro()).toBe('Informe ao menos um número.');
  });
});
