// Forma de los datos TAL COMO LOS USA EL CLIENTE. Sigue el contrato
// acordado de GET /unidades/{id} (docs/contrato/openapi.yaml).

export type EstadoUnidad = 'DISPONIBLE' | 'PRESTADA' | 'EN_REVISION' | 'FUERA_DE_SERVICIO';

export interface TipoEquipo {
  id: number;
  nombre: string;
  duracionMaximaDias: number;
  requiereAutorizacion: boolean;
}

export interface Unidad {
  id: number;
  numeroInventario: string;
  estado: EstadoUnidad;
  tipo: TipoEquipo;
}

// Spring Data envuelve las respuestas paginadas con esta estructura.
export interface PaginaUnidades {
  content: Unidad[];
  number: number;          // página actual (empieza en 0)
  totalElements: number;   // total de unidades en todas las páginas
}

// Texto que ve el usuario. El valor técnico del contrato (EN_REVISION) no se muestra tal cual.
export const ETIQUETA_ESTADO: Record<EstadoUnidad, string> = {
  DISPONIBLE: 'Disponible',
  PRESTADA: 'Prestada',
  EN_REVISION: 'En revisión',
  FUERA_DE_SERVICIO: 'Fuera de servicio',
};
