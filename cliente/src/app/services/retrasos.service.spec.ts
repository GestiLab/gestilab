import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { RetrasosService } from './retrasos.service';
import { environment } from '../../environments/environment';
import { Retraso } from '../models/retraso';

// Datos de ejemplo con la forma del contrato: ninguna prueba toca la API real.
const RETRASOS: Retraso[] = [
  { prestamoId: 7, unidadId: 12, solicitanteId: 'sub-ana', fechaDevolucionPrevista: '2026-11-20', diasRetraso: 6 },
  { prestamoId: 9, unidadId: 31, solicitanteId: 'sub-luis', fechaDevolucionPrevista: '2026-11-10', diasRetraso: 16 },
];

describe('RetrasosService', () => {
  let servicio: RetrasosService;
  let http: HttpTestingController;
  const url = `${environment.prestamosApiUrl}/prestamos/retrasos`;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    servicio = TestBed.inject(RetrasosService);
    http = TestBed.inject(HttpTestingController);
  });

  // Falla si quedó alguna petición sin responder: ninguna prueba toca la red real.
  afterEach(() => {
    vi.useRealTimers();
    http.verify();
  });

  const pedida = () => http.expectOne(url);

  describe('SCRUM-38 CA-1 · lista de retrasos', () => {
    it('pide la lista con GET a la dirección del entorno', () => {
      servicio.cargar();
      expect(pedida().request.method).toBe('GET');
    });

    it('guarda la lista tal como llega, con sus días de retraso', () => {
      servicio.cargar();
      pedida().flush(RETRASOS);

      expect(servicio.retrasos()).toEqual(RETRASOS);
      expect(servicio.retrasos().map((r) => r.diasRetraso)).toEqual([6, 16]);
      expect(servicio.mostrarLista()).toBe(true);
      expect(servicio.sinRetrasos()).toBe(false);
    });

    it('mientras espera, "cargando" es verdadero y no hay lista a la vista', () => {
      servicio.cargar();
      expect(servicio.cargando()).toBe(true);
      expect(servicio.mostrarLista()).toBe(false);

      pedida().flush(RETRASOS);
      expect(servicio.cargando()).toBe(false);
    });
  });

  describe('SCRUM-38 CA-2 · sin retrasos', () => {
    it('una lista vacía se reconoce como "no hay retrasos"', () => {
      servicio.cargar();
      pedida().flush([]);

      expect(servicio.sinRetrasos()).toBe(true);
      expect(servicio.mostrarLista()).toBe(false);
      expect(servicio.error()).toBeNull();
    });

    it('antes de la primera respuesta todavía no se dice "no hay retrasos"', () => {
      servicio.cargar();
      expect(servicio.sinRetrasos()).toBe(false);
      pedida().flush([]);
    });
  });

  describe('SCRUM-38 CA-3 · carga y error', () => {
    it('si el servicio no responde, el error es legible y la carga vuelve a falso', () => {
      servicio.cargar();
      pedida().error(new ProgressEvent('error'), { status: 0 });

      expect(servicio.error()).toBe('No hay conexión con el servicio de préstamos. Intenta de nuevo más tarde.');
      expect(servicio.cargando()).toBe(false);
      expect(servicio.mostrarLista()).toBe(false);
      expect(servicio.sinRetrasos()).toBe(false);
    });

    it('un 503 se explica como servicio no disponible', () => {
      servicio.cargar();
      pedida().flush({}, { status: 503, statusText: 'Service Unavailable' });
      expect(servicio.error()).toBe('El servicio de préstamos no está disponible. Intenta más tarde.');
    });

    it('un 403 dice que no tiene permiso', () => {
      servicio.cargar();
      pedida().flush({}, { status: 403, statusText: 'Forbidden' });
      expect(servicio.error()).toBe('No tienes permiso para ver los retrasos.');
    });

    it('si la respuesta de error trae mensaje, lo usa', () => {
      servicio.cargar();
      pedida().flush({ mensaje: 'Algo falló' }, { status: 500, statusText: 'Server Error' });
      expect(servicio.error()).toBe('Error 500: Algo falló');
    });

    it('"reintentar" vuelve a pedir la lista y limpia el error', () => {
      servicio.cargar();
      pedida().error(new ProgressEvent('error'), { status: 0 });
      expect(servicio.error()).not.toBeNull();

      servicio.reintentar();
      expect(servicio.error()).toBeNull();
      expect(servicio.cargando()).toBe(true);

      pedida().flush(RETRASOS);
      expect(servicio.retrasos()).toEqual(RETRASOS);
      expect(servicio.error()).toBeNull();
    });

    it('si se pide dos veces seguidas, la primera petición se descarta', () => {
      servicio.cargar();
      servicio.cargar();

      const peticiones = http.match(url);
      expect(peticiones.length).toBe(2);
      expect(peticiones[0].cancelled).toBe(true);
      peticiones[1].flush(RETRASOS);
    });
  });

  describe('SCRUM-38 CA-4 · los días salen de la respuesta', () => {
    it('no cambian aunque la fecha del navegador sea otra', () => {
      vi.useFakeTimers();
      vi.setSystemTime(new Date('2035-01-01T12:00:00Z'));   // años después de las fechas de los préstamos

      servicio.cargar();
      pedida().flush(RETRASOS);

      expect(servicio.retrasos().map((r) => r.diasRetraso)).toEqual([6, 16]);
    });
  });
});
