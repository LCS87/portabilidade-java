import { Component, signal } from '@angular/core';
import { RouterOutlet } from '@angular/router';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet],
  template: `
    <header class="navbar">
      <span class="brand">⚡ Portabilidade</span>
      <nav>
        <a routerLink="/consulta" routerLinkActive="active">Consulta</a>
        <a routerLink="/lote" routerLinkActive="active">Processar Lote</a>
        <a routerLink="/dashboard" routerLinkActive="active">Dashboard</a>
      </nav>
    </header>
    <main class="content">
      <router-outlet />
    </main>
    <footer class="footer">
      BFF {{ bff() }} · Fase 3 — Angular 20 standalone + signals
    </footer>
  `,
  styles: [
    `
      .navbar {
        display: flex;
        align-items: center;
        gap: 2rem;
        padding: 0.75rem 1.5rem;
        background: #1e293b;
        color: #fff;
      }
      .brand {
        font-weight: 700;
        font-size: 1.1rem;
      }
      nav a {
        color: #cbd5e1;
        text-decoration: none;
        margin-right: 1rem;
        padding: 0.25rem 0.5rem;
        border-radius: 4px;
      }
      nav a.active {
        background: #334155;
        color: #fff;
      }
      .content {
        max-width: 1100px;
        margin: 1.5rem auto;
        padding: 0 1rem;
      }
      .footer {
        text-align: center;
        color: #94a3b8;
        font-size: 0.8rem;
        padding: 1rem;
      }
    `,
  ],
})
export class App {
  protected readonly bff = signal('http://localhost:8081');
}
