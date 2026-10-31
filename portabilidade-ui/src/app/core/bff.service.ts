import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';

export interface ItemConsulta {
  numero: string;
  canonical: string;
  operadora: string | null;
  fonte: string | null;
  mascarado: string | null;
  encontrada: boolean;
}

export interface ConsultaResponse {
  total: number;
  encontradas: number;
  itens: ItemConsulta[];
}

export interface ResumoDashboard {
  totalConsultas: number;
  resolvidas: number;
  taxaAcerto: number;
  topOperadoras: { operadora: string; quantidade: number }[];
  porFonte: { fonte: string; quantidade: number }[];
}

export interface BatchStatus {
  jobId?: string;
  status: string;
  processados?: number;
  total?: number;
  erros?: number;
  [k: string]: unknown;
}

/** Serviço de acesso ao BFF — endpoints orientados a tela (Fase 3). */
@Injectable({ providedIn: 'root' })
export class BffService {
  private readonly http = inject(HttpClient);
  private readonly base = environment.bffBaseUrl;

  consultar(numeros: string[]): Observable<ConsultaResponse> {
    return this.http.post<ConsultaResponse>(`${this.base}/consulta`, { numeros });
  }

  resumo(
    consultas: { numero: string; operadora: string | null; fonte: string | null }[],
  ): Observable<ResumoDashboard> {
    return this.http.post<ResumoDashboard>(`${this.base}/dashboard/resumo`, { consultas });
  }

  enviarLote(arquivo: File): Observable<{ jobId: string; status: string }> {
    const form = new FormData();
    form.append('arquivo', arquivo);
    return this.http.post<{ jobId: string; status: string }>(`${this.base}/batch`, form);
  }

  statusLote(jobId: string): Observable<BatchStatus> {
    return this.http.get<BatchStatus>(`${this.base}/batch/${jobId}`);
  }

  urlResultadoLote(jobId: string): string {
    return `${this.base}/batch/${jobId}/resultado`;
  }

  operadoras(): Observable<{ codigo: string; nome: string }[]> {
    return this.http.get<{ codigo: string; nome: string }[]>(`${this.base}/operadoras`);
  }
}
