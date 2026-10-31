import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { CommonModule } from '@angular/common';
import { BffService, ItemConsulta } from '../../core/bff.service';

/** Tela de consulta sincrona via BFF (/bff/v1/consulta). */
@Component({
  selector: 'app-consulta',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <h2>Consulta de portabilidade</h2>
    <p class="hint">Informe até 500 números (um por linha ou separados por vírgula).</p>
    <textarea [(ngModel)]="entrada" rows="6" placeholder="+5511999999999&#10;+5521988887777"></textarea>
    <div class="actions">
      <button (click)="consultar()" [disabled]="carregando()">
        {{ carregando() ? 'Consultando…' : 'Consultar' }}
      </button>
      <button class="secondary" (click)="limpar()" [disabled]="carregando()">Limpar</button>
    </div>
    @if (erro()) {
      <p class="error">{{ erro() }}</p>
    }
    @if (result()) {
      <p class="summary">
        {{ result()!.total }} números · {{ result()!.encontradas }} com operadora resolvida
      </p>
      <table>
        <thead>
          <tr><th>Número</th><th>Canonical</th><th>Operadora</th><th>Fonte</th><th>Status</th></tr>
        </thead>
        <tbody>
          @for (item of itens(); track item.numero) {
            <tr>
              <td>{{ item.numero }}</td>
              <td>{{ item.canonical }}</td>
              <td>{{ item.operadora ?? '—' }}</td>
              <td>{{ item.fonte ?? '—' }}</td>
              <td>
                <span [class]="item.encontrada ? 'badge ok' : 'badge miss'">
                  {{ item.encontrada ? 'resolvida' : 'não encontrada' }}
                </span>
              </td>
            </tr>
          }
        </tbody>
      </table>
    }
  `,
  styles: [
    `
      textarea { width: 100%; font-family: monospace; padding: 0.5rem; }
      .actions { margin: 0.5rem 0 1rem; display: flex; gap: 0.5rem; }
      button {
        padding: 0.5rem 1rem; border: none; border-radius: 6px;
        background: #2563eb; color: #fff; cursor: pointer;
      }
      button.secondary { background: #64748b; }
      button:disabled { opacity: 0.5; cursor: not-allowed; }
      table { width: 100%; border-collapse: collapse; }
      th, td { text-align: left; padding: 0.4rem 0.6rem; border-bottom: 1px solid #e2e8f0; }
      .badge { padding: 2px 8px; border-radius: 10px; font-size: 0.75rem; }
      .badge.ok { background: #dcfce7; color: #166534; }
      .badge.miss { background: #fee2e2; color: #991b1b; }
      .error { color: #b91c1c; }
      .summary { color: #334155; font-weight: 600; }
      .hint { color: #64748b; font-size: 0.85rem; }
    `,
  ],
})
export class ConsultaComponent {
  private readonly bff = inject(BffService);

  entrada = '';
  readonly carregando = signal(false);
  readonly erro = signal<string | null>(null);
  readonly result = signal<{ total: number; encontradas: number } | null>(null);
  readonly itens = signal<ItemConsulta[]>([]);

  consultar(): void {
    const numeros = this.entrada
      .split(/[\s,;]+/)
      .map((n) => n.trim())
      .filter((n) => n.length > 0);
    this.erro.set(null);
    if (numeros.length === 0) {
      this.erro.set('Informe ao menos um número.');
      return;
    }
    this.carregando.set(true);
    this.bff.consultar(numeros).subscribe({
      next: (r) => {
        this.result.set({ total: r.total, encontradas: r.encontradas });
        this.itens.set(r.itens);
        // Persiste para o dashboard (sessão local).
        try {
          localStorage.setItem(
            'ultima-consulta',
            JSON.stringify(
              r.itens.map((i) => ({ numero: i.numero, operadora: i.operadora, fonte: i.fonte })),
            ),
          );
        } catch {
          /* storage indisponível — segue sem persistir */
        }
        this.carregando.set(false);
      },
      error: (e) => {
        this.erro.set(e?.error?.detail ?? e?.message ?? 'Falha ao consultar o BFF.');
        this.carregando.set(false);
      },
    });
  }

  limpar(): void {
    this.entrada = '';
    this.result.set(null);
    this.itens.set([]);
    this.erro.set(null);
  }
}
