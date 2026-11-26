import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { CatalogoService } from './catalogo.service';
import { environment } from '../../environments/environment';
import { PaginaUnidades, TipoEquipo } from '../models/unidad';

describe('CatalogoService', () => {
  let servicio: CatalogoService;
  let http: HttpTestingController;
  const url = `${environment.apiUrl}/unidades`;
  const urlTipos = `${environment.apiUrl}/tipos-equipo`;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    servicio = TestBed.inject(CatalogoService);
    http = TestBed.inject(HttpTestingController);
  });

  // Falla si quedó alguna petición sin responder: ninguna prueba toca la red real.
  afterEach(() => http.verify());

  const pedida = () => http.expectOne((r) => r.url === url);
  const responder = (number: number, totalElements: number) =>
    pedida().flush({ number, totalElements, content: [] });

  // ── CA-4 y CA-5: estado y error en señales ──────────────────────────────
  it('CA-4: al pedir una página expone página, total, carga y error en señales', () => {
    const pagina: PaginaUnidades = {
      number: 2,
      totalElements: 120,
      content: [{
        id: 14, numeroInventario: 'MULT-014', estado: 'DISPONIBLE',
        tipo: { id: 2, nombre: 'Multímetro', duracionMaximaDias: 7, requiereAutorizacion: false },
      }],
    };

    servicio.cargarPagina(2, 20);
    expect(servicio.cargando()).toBe(true);            // mientras espera la respuesta

    const peticion = pedida();
    expect(peticion.request.params.get('page')).toBe('2');
    expect(peticion.request.params.get('size')).toBe('20');
    peticion.flush(pagina);

    expect(servicio.paginaActual()).toBe(2);
    expect(servicio.totalElementos()).toBe(120);
    expect(servicio.unidades()[0].numeroInventario).toBe('MULT-014');
    expect(servicio.cargando()).toBe(false);
    expect(servicio.error()).toBeNull();
  });

  it('CA-5: si la API no responde, el error es legible y la carga vuelve a falso', () => {
    servicio.cargarPagina();

    // status 0 = no hubo respuesta (servidor caído o sin red)
    pedida().error(new ProgressEvent('error'), { status: 0 });

    expect(servicio.error()).toContain('No hay conexión');
    expect(servicio.cargando()).toBe(false);
  });

  it('CA-5: si la API responde con el error común, usa su mensaje', () => {
    servicio.cargarPagina();

    pedida().flush(
      { status: 404, error: 'Not Found', mensaje: 'Unidad 99 no encontrada', timestamp: '2026-11-09T10:00:00' },
      { status: 404, statusText: 'Not Found' },
    );

    expect(servicio.error()).toBe('Error 404: Unidad 99 no encontrada');
    expect(servicio.cargando()).toBe(false);
  });

  it('CA-5: un 503 se explica como servicio no disponible', () => {
    servicio.cargarPagina();
    pedida().flush('', { status: 503, statusText: 'Service Unavailable' });

    expect(servicio.error()).toContain('no está disponible');
  });

  it('CA-5: si el error no trae mensaje, usa el texto del estado HTTP', () => {
    servicio.cargarPagina();
    pedida().flush('fallo', { status: 500, statusText: 'Server Error' });

    expect(servicio.error()).toBe('Error 500: Server Error');
  });

  it('al pedir otra página después de un error, el error se limpia', () => {
    servicio.cargarPagina();
    pedida().error(new ProgressEvent('error'), { status: 0 });
    expect(servicio.error()).not.toBeNull();

    servicio.cargarPagina();
    expect(servicio.error()).toBeNull();
    pedida().flush({ number: 0, totalElements: 0, content: [] });
  });

  it('una petición nueva cancela la anterior que seguía en vuelo', () => {
    servicio.cargarPagina(0);
    const primera = pedida();

    servicio.cargarPagina(1);
    expect(primera.cancelled).toBe(true);

    const segunda = http.expectOne((r) => r.url === url && r.params.get('page') === '1');
    segunda.flush({ number: 1, totalElements: 120, content: [] });
    expect(servicio.paginaActual()).toBe(1);
  });

  // ── Tipos para el selector ──────────────────────────────────────────────
  it('cargarTipos deja la lista en la señal; si falla, queda vacía', () => {
    const tipos: TipoEquipo[] = [
      { id: 1, nombre: 'Osciloscopio', duracionMaximaDias: 3, requiereAutorizacion: true },
    ];

    servicio.cargarTipos();
    http.expectOne(urlTipos).flush(tipos);
    expect(servicio.tipos()).toEqual(tipos);

    servicio.cargarTipos();
    http.expectOne(urlTipos).flush('', { status: 500, statusText: 'Server Error' });
    expect(servicio.tipos()).toEqual([]);
  });

  // ── CA-7: filtros y parámetros ──────────────────────────────────────────
  it('solo envía los filtros que el usuario puso', () => {
    servicio.cargarPagina();
    const sinFiltros = pedida();
    expect(sinFiltros.request.params.has('tipoId')).toBe(false);
    expect(sinFiltros.request.params.has('estado')).toBe(false);
    expect(sinFiltros.request.params.has('nombre')).toBe(false);
    sinFiltros.flush({ number: 0, totalElements: 0, content: [] });

    servicio.cambiarTipo(2);
    const conTipo = pedida();
    expect(conTipo.request.params.get('tipoId')).toBe('2');
    conTipo.flush({ number: 0, totalElements: 0, content: [] });

    servicio.cambiarSoloDisponibles(true);
    const conEstado = pedida();
    expect(conEstado.request.params.get('estado')).toBe('DISPONIBLE');
    conEstado.flush({ number: 0, totalElements: 0, content: [] });

    servicio.buscar('multi');
    const conTodos = pedida();
    expect(conTodos.request.params.get('nombre')).toBe('multi');
    expect(conTodos.request.params.get('tipoId')).toBe('2');
    conTodos.flush({ number: 0, totalElements: 0, content: [] });

    // Quitar cada filtro deja de enviarlo
    servicio.cambiarTipo(null);
    responder(0, 0);
    servicio.cambiarSoloDisponibles(false);
    responder(0, 0);
    servicio.buscar('');
    const sinNada = pedida();
    expect(sinNada.request.params.has('tipoId')).toBe(false);
    expect(sinNada.request.params.has('estado')).toBe(false);
    expect(sinNada.request.params.has('nombre')).toBe(false);
    sinNada.flush({ number: 0, totalElements: 0, content: [] });
  });

  it('limpiarFiltros olvida los filtros guardados', () => {
    servicio.cambiarTipo(2);
    responder(0, 0);

    servicio.limpiarFiltros();
    servicio.cargarPagina();

    const peticion = pedida();
    expect(peticion.request.params.has('tipoId')).toBe(false);
    peticion.flush({ number: 0, totalElements: 0, content: [] });
  });

  // ── CA-9: estado calculado con computed ─────────────────────────────────
  it('CA-9: totalPaginas y botones se recalculan solos al cambiar la página', () => {
    servicio.cargarPagina(0);
    responder(0, 120);
    expect(servicio.totalPaginas()).toBe(6);
    expect(servicio.anteriorDeshabilitado()).toBe(true);
    expect(servicio.siguienteDeshabilitado()).toBe(false);

    servicio.siguiente();
    responder(1, 120);
    expect(servicio.anteriorDeshabilitado()).toBe(false);

    servicio.cargarPagina(5);
    responder(5, 120);
    expect(servicio.siguienteDeshabilitado()).toBe(true);
  });

  it('CA-9: cambiar un filtro vuelve a la página 0 y sin resultados no hay páginas', () => {
    servicio.cargarPagina(3);
    responder(3, 120);

    servicio.cambiarTipo(2);
    expect(servicio.paginaActual()).toBe(0);
    responder(0, 0);

    expect(servicio.totalPaginas()).toBe(0);
    expect(servicio.sinResultados()).toBe(true);
    expect(servicio.anteriorDeshabilitado()).toBe(true);
    expect(servicio.siguienteDeshabilitado()).toBe(true);
  });

  it('los botones quedan deshabilitados mientras llega la respuesta', () => {
    servicio.cargarPagina(2);
    expect(servicio.anteriorDeshabilitado()).toBe(true);
    expect(servicio.siguienteDeshabilitado()).toBe(true);

    responder(2, 120);
    expect(servicio.anteriorDeshabilitado()).toBe(false);
    expect(servicio.siguienteDeshabilitado()).toBe(false);
  });

  it('anterior y siguiente no piden nada en los límites', () => {
    servicio.cargarPagina(0);
    responder(0, 120);
    servicio.anterior();
    http.expectNone((r) => r.url === url);

    servicio.cargarPagina(5);
    responder(5, 120);
    servicio.siguiente();
    http.expectNone((r) => r.url === url);
  });
  // ── Actualización automática: temporizador simulado, no se espera tiempo real ──
  describe('actualización automática', () => {
    const unidad = (estado: 'DISPONIBLE' | 'PRESTADA'): PaginaUnidades => ({
      number: 0,
      totalElements: 1,
      content: [{
        id: 1, numeroInventario: 'MULT-001', estado,
        tipo: { id: 2, nombre: 'Multímetro', duracionMaximaDias: 7, requiereAutorizacion: false },
      }],
    });

    // Deja el catálogo con su primera respuesta y la actualización encendida.
    function abrirCatalogo(estado: 'DISPONIBLE' | 'PRESTADA' = 'DISPONIBLE') {
      servicio.cargarPagina();
      pedida().flush(unidad(estado));
      servicio.iniciarActualizacion();
    }

    beforeEach(() => vi.useFakeTimers());
    afterEach(() => {
      servicio.detenerActualizacion();
      vi.useRealTimers();
    });

    it('SCRUM-39 CA-1: una entrega se ve en pantalla en el siguiente turno, sin recargar', () => {
      abrirCatalogo('DISPONIBLE');
      expect(servicio.unidades()[0].estado).toBe('DISPONIBLE');

      vi.advanceTimersByTime(2000);
      pedida().flush(unidad('PRESTADA'));

      expect(servicio.unidades()[0].estado).toBe('PRESTADA');
    });

    it('SCRUM-39 CA-2: una devolución se ve en pantalla en el siguiente turno', () => {
      abrirCatalogo('PRESTADA');

      vi.advanceTimersByTime(2000);
      pedida().flush(unidad('DISPONIBLE'));

      expect(servicio.unidades()[0].estado).toBe('DISPONIBLE');
    });

    it('al actualizarse no parpadea: no pasa por "cargando" y la lista sigue visible', () => {
      abrirCatalogo();

      vi.advanceTimersByTime(2000);
      expect(servicio.cargando()).toBe(false);
      expect(servicio.mostrarLista()).toBe(true);
      pedida().flush(unidad('PRESTADA'));
      expect(servicio.mostrarLista()).toBe(true);
    });

    it('SCRUM-39 CA-3: pide la misma página con los mismos filtros', () => {
      servicio.cambiarTipo(2);
      responder(0, 120);
      servicio.cambiarSoloDisponibles(true);
      responder(0, 120);
      servicio.siguiente();
      responder(1, 120);
      servicio.iniciarActualizacion();

      vi.advanceTimersByTime(2000);
      const peticion = pedida();
      expect(peticion.request.params.get('page')).toBe('1');
      expect(peticion.request.params.get('tipoId')).toBe('2');
      expect(peticion.request.params.get('estado')).toBe('DISPONIBLE');
      peticion.flush({ number: 1, totalElements: 120, content: [] });

      expect(servicio.paginaActual()).toBe(1);
    });

    it('SCRUM-39 CA-4: al detenerla no se hacen más consultas, ni siquiera una que estaba en vuelo', () => {
      abrirCatalogo();
      vi.advanceTimersByTime(2000);
      const enVuelo = pedida();

      servicio.detenerActualizacion();
      expect(enVuelo.cancelled).toBe(true);

      vi.advanceTimersByTime(20000);
      http.expectNone((r) => r.url === url);
    });

    it('encenderla dos veces deja un solo temporizador y una sola consulta por turno', () => {
      abrirCatalogo();
      servicio.iniciarActualizacion();
      expect(vi.getTimerCount()).toBe(1);     // si no limpiara el anterior, serían 2

      vi.advanceTimersByTime(2000);
      pedida().flush(unidad('DISPONIBLE'));
    });

    it('SCRUM-39 CA-5: si una consulta falla, la lista no se borra y se reintenta en el siguiente turno', () => {
      abrirCatalogo('DISPONIBLE');

      vi.advanceTimersByTime(2000);
      pedida().error(new ProgressEvent('error'), { status: 0 });
      expect(servicio.unidades()[0].estado).toBe('DISPONIBLE');
      expect(servicio.error()).toBeNull();
      expect(servicio.mostrarLista()).toBe(true);

      vi.advanceTimersByTime(2000);
      pedida().flush(unidad('PRESTADA'));
      expect(servicio.unidades()[0].estado).toBe('PRESTADA');
    });

    it('no apila consultas: si la anterior no ha respondido, espera', () => {
      abrirCatalogo();

      vi.advanceTimersByTime(2000);
      const primera = pedida();
      vi.advanceTimersByTime(2000);
      http.expectNone((r) => r.url === url);   // no salió una segunda consulta
      primera.flush(unidad('DISPONIBLE'));
    });

    it('no consulta antes de que llegue la primera respuesta', () => {
      servicio.iniciarActualizacion();

      vi.advanceTimersByTime(2000);
      http.expectNone((r) => r.url === url);
    });

    it('no consulta mientras hay un error a la vista: ahí la persona usa "Reintentar"', () => {
      servicio.cargarPagina();
      pedida().error(new ProgressEvent('error'), { status: 0 });
      servicio.iniciarActualizacion();

      vi.advanceTimersByTime(2000);
      http.expectNone((r) => r.url === url);
    });

    it('no consulta mientras la persona espera una página que pidió', () => {
      abrirCatalogo();
      servicio.cargarPagina(1);

      vi.advanceTimersByTime(2000);
      expect(http.match((r) => r.url === url).length).toBe(1);   // solo la de la persona
    });

    it('si la persona cambia de página, se descarta la consulta periódica que iba a medias', () => {
      abrirCatalogo();
      vi.advanceTimersByTime(2000);
      const periodica = pedida();

      servicio.cargarPagina(1);
      expect(periodica.cancelled).toBe(true);
      http.expectOne((r) => r.url === url && r.params.get('page') === '1').flush({ number: 1, totalElements: 120, content: [] });
    });
  });
});
