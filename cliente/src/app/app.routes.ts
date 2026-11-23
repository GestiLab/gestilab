import { Routes } from '@angular/router';
import { Catalogo } from './components/catalogo/catalogo';
import { Inicio } from './components/inicio/inicio';
import { rolPermitido } from './auth/rol-permitido';
import { sesionIniciada } from './auth/sesion-iniciada';
import { ROLES_CATALOGO } from './auth/roles';

// Cada pantalla agrega aquí su ruta cuando se construye.
// `title` es lo que aparece en la pestaña del navegador.
// La raíz es pública (CA-7); las demás exigen sesión (CA-4) y el rol que declaran (CA-5).
export const routes: Routes = [
  { path: '', component: Inicio, title: 'Inicio · GestiLab', pathMatch: 'full' },
  {
    path: 'catalogo',
    component: Catalogo,
    title: 'Catálogo de equipos · GestiLab',
    canActivate: [sesionIniciada, rolPermitido],
    data: { roles: ROLES_CATALOGO },
  },
];
