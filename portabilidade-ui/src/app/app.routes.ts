import { Routes } from '@angular/router';

/** Lazy loading por rota (esbuild code-splitting) — Fase 3. */
export const routes: Routes = [
  { path: '', redirectTo: 'consulta', pathMatch: 'full' },
  {
    path: 'consulta',
    loadComponent: () => import('./features/consulta/consulta').then((m) => m.ConsultaComponent),
  },
  {
    path: 'lote',
    loadComponent: () => import('./features/lote/lote').then((m) => m.LoteComponent),
  },
  {
    path: 'dashboard',
    loadComponent: () => import('./features/dashboard/dashboard').then((m) => m.DashboardComponent),
  },
];
