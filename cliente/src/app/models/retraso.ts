// Forma de un renglón del listado de retrasos TAL COMO LO ENTREGA el servicio de préstamos
// (GET /prestamos/retrasos). Los días de retraso los calcula el servicio, no el cliente.

export interface Retraso {
  prestamoId: number;
  unidadId: number;
  solicitanteId: string;              // el identificador (sub) de la persona, no su nombre
  fechaDevolucionPrevista: string;    // "AAAA-MM-DD"
  diasRetraso: number;
}
