import { Routes } from '@angular/router';
import { Catalogo } from './components/catalogo/catalogo';

// Rutas base. Cada pantalla agrega aquí su ruta cuando se construye.
// `title` es lo que aparece en la pestaña del navegador.
export const routes: Routes = [
  { path: '', redirectTo: 'catalogo', pathMatch: 'full' },
  { path: 'catalogo', component: Catalogo, title: 'Catálogo de equipos · GestiLab' },
];
