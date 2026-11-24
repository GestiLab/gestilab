// Los dos roles del realm gestilab. El texto debe ser idéntico al del realm.
export type Rol = 'usuario' | 'auxiliar';

// Qué roles pueden entrar a cada pantalla. Menú y rutas leen de aquí,
// así no se escribe el permiso en dos sitios que luego se desincronizan.
export const ROLES_CATALOGO: Rol[] = ['usuario', 'auxiliar'];

// Una opción del menú: solo la ve quien tenga alguno de sus roles (CA-5).
export interface OpcionMenu {
  texto: string;
  ruta: string;
  roles: Rol[];
}

// Cada pantalla de rol que se construya agrega aquí su opción.
export const OPCIONES_MENU: OpcionMenu[] = [
  { texto: 'Catálogo', ruta: '/catalogo', roles: ROLES_CATALOGO },
];
