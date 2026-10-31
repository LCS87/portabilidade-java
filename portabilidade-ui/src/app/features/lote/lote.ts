import { Component, OnDestroy, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { BffService, BatchStatus } from '../../core/bff.service';

/** Tela de upload de lote: submit + progresso por polling + download do resultado. */
@Component({
  selector: 'app-lote',
  standalone: true,
  imports: [CommonModule],
  template: `
    <h2>Processar lote (CSV / XLSX)</h2>
    <input type="file" accept=".csv,.xlsx" (change)="onArquivo($event)" [disabled]="enviando()" />
    @if (erro()) { <p class="error">{{ erro() }}</p> }
    @if (jobId()) {
      <div class="status">
        <p><strong>Job:</strong> {{ jobId() }}</p>
        <p><strong>Status:</strong> {{ status()?.status ?? '—' }}</p>
        @if (total > 0) {
          <progress [value]="processados" [max]="total"></progress>
          <p>{{ processados }} / {{ total }} linhas · erros: {{ erros }}</p>
        }
        @if (concluido()) {
          <a class="download" [href]="urlResultado()">⬇ Baixar resultado (CSV)</a>
        }
      </div>
    }
  `,
  styles: [
    `
      progress { width: 100%; height: 1rem; }
      .status { margin-top: 1rem; padding: 1rem; background: #f8fafc; border-radius: 8px; }
      .download {
        display: inline-block; margin-top: 0.5rem; padding: 0.5rem 1rem;
        background: #16a34a; color: #fff; border-radius: 6px; text-decoration: none;
      }
      .error { color: #b91c1c; }
    `,
  ],
})
export class LoteComponent implements OnDestroy {
  private readonly bff = inject(BffService);

  readonly jobId = signal<string | null>(null);
  readonly status = signal<BatchStatus | null>(null);
  readonly enviando = signal(false);
  readonly concluido = signal(false);
  readonly erro = signal<string | null>(null);

  get processados(): number { return this.status()?.processados ?? 0; }
  get total(): number { return this.status()?.total ?? 0; }
  get erros(): number { return this.status()?.erros ?? 0; }

  private timer?: ReturnType<typeof setInterval>;

  onArquivo(event: Event): void {
    const file = (event.target as HTMLInputElement).files?.[0];
    if (!file) return;
    this.erro.set(null);
    this.concluido.set(false);
    this.enviando.set(true);
    this.bff.enviarLote(file).subscribe({
      next: (r) => {
        this.enviando.set(false);
        this.jobId.set(r.jobId);
        this.iniciarPolling(r.jobId);
      },
      error: (e) => {
        this.enviando.set(false);
        this.erro.set(e?.error?.detail ?? e?.message ?? 'Falha ao enviar o arquivo.');
      },
    });
  }

  /** Polling a cada 2s (alternativa simples ao SSE — funciona atrás de proxies). */
  private iniciarPolling(id: string): void {
    this.pararPolling();
    this.timer = setInterval(() => {
      this.bff.statusLote(id).subscribe({
        next: (s) => {
          this.status.set(s);
          const done = ['COMPLETED', 'FAILED', 'DONE', 'CONCLUIDO'].includes(
            String(s.status).toUpperCase(),
          );
          if (done) {
            this.concluido.set(String(s.status).toUpperCase() !== 'FAILED');
            this.pararPolling();
          }
        },
        error: () => this.pararPolling(),
      });
    }, 2000);
  }

  urlResultado(): string {
    return this.bff.urlResultadoLote(this.jobId()!);
  }

  private pararPolling(): void {
    if (this.timer) clearInterval(this.timer);
    this.timer = undefined;
  }

  ngOnDestroy(): void {
    this.pararPolling();
  }
}
