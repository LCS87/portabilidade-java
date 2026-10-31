import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { BffService, ResumoDashboard } from '../../core/bff.service';

/** Dashboard: métricas agregadas pela última sessão de consultas da UI. */
@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [CommonModule],
  template: `
    <h2>Dashboard</h2>
    @if (erro()) { <p class="error">{{ erro() }}</p> }
    @if (resumo(); as r) {
      <div class="cards">
        <div class="card"><span class="num">{{ r.totalConsultas }}</span><span>consultas</span></div>
        <div class="card"><span class="num">{{ r.resolvidas }}</span><span>resolvidas</span></div>
        <div class="card"><span class="num">{{ r.taxaAcerto }}%</span><span>taxa de acerto</span></div>
      </div>
      <div class="columns">
        <section>
          <h3>Top operadoras</h3>
          <ul>
            @for (o of r.topOperadoras; track o.operadora) {
              <li>{{ o.operadora }} — <strong>{{ o.quantidade }}</strong></li>
            } @empty { <li class="muted">Sem dados ainda.</li> }
          </ul>
        </section>
        <section>
          <h3>Por fonte de resolução</h3>
          <ul>
            @for (f of r.porFonte; track f.fonte) {
              <li>{{ f.fonte }} — <strong>{{ f.quantidade }}</strong></li>
            } @empty { <li class="muted">Sem dados ainda.</li> }
          </ul>
        </section>
      </div>
    } @else {
      <p class="muted">Faça consultas na aba "Consulta" e clique em Atualizar para ver métricas.</p>
    }
    <button (click)="atualizar()" [disabled]="carregando()">
      {{ carregando() ? 'Atualizando…' : 'Atualizar com última consulta' }}
    </button>
  `,
  styles: [
    `
      .cards { display: flex; gap: 1rem; margin-bottom: 1.5rem; }
      .card {
        flex: 1; padding: 1rem; background: #f8fafc; border-radius: 8px;
        display: flex; flex-direction: column; align-items: center;
      }
      .card .num { font-size: 1.8rem; font-weight: 700; color: #1d4ed8; }
      .columns { display: flex; gap: 2rem; }
      section { flex: 1; }
      ul { list-style: none; padding: 0; }
      li { padding: 0.3rem 0; border-bottom: 1px solid #e2e8f0; }
      button {
        margin-top: 1.5rem; padding: 0.5rem 1rem; border: none; border-radius: 6px;
        background: #2563eb; color: #fff; cursor: pointer;
      }
      .muted { color: #64748b; }
      .error { color: #b91c1c; }
    `,
  ],
})
export class DashboardComponent implements OnInit {
  private readonly bff = inject(BffService);

  readonly resumo = signal<ResumoDashboard | null>(null);
  readonly carregando = signal(false);
  readonly erro = signal<string | null>(null);

  /** Última consulta feita pelo usuário (mesma origem de dados do plano BFF). */
  private ultimaConsulta: { numero: string; operadora: string | null; fonte: string | null }[] = [];

  ngOnInit(): void {
    // Recupera histórico persistido pela tela de consulta (localStorage por sessão).
    try {
      const raw = localStorage.getItem('ultima-consulta');
      if (raw) this.ultimaConsulta = JSON.parse(raw);
    } catch {
      this.ultimaConsulta = [];
    }
    if (this.ultimaConsulta.length > 0) this.atualizar();
  }

  atualizar(): void {
    if (this.ultimaConsulta.length === 0) {
      this.erro.set('Nenhuma consulta realizada ainda.');
      return;
    }
    this.carregando.set(true);
    this.erro.set(null);
    this.bff.resumo(this.ultimaConsulta).subscribe({
      next: (r) => {
        this.resumo.set(r);
        this.carregando.set(false);
      },
      error: (e) => {
        this.erro.set(e?.error?.detail ?? 'Falha ao carregar métricas.');
        this.carregando.set(false);
      },
    });
  }
}
