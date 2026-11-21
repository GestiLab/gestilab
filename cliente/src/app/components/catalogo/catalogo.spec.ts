import { TestBed, ComponentFixture } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Catalogo } from './catalogo';
import { environment } from '../../../environments/environment';
import { PaginaUnidades, TipoEquipo, Unidad } from '../../models/unidad';

// Datos de ejemplo: ninguna prueba toca la API real.
const TIPOS: TipoEquipo[] = [
  { id: 1, nombre: 'Osciloscopio', duracionMaximaDias: 3, requiereAutorizacion: true },
  { id: 2, nombre: 'Multímetro', duracionMaximaDias: 7, requiereAutorizacion: false },
];

function unidades(cuantas: number, desde = 1): Unidad[] {
  return Array.from({ length: cuantas }, (_, i) => ({
    id: desde + i,
    numeroInventario: `MULT-${String(desde + i).padStart(3, '0')}`,
    estado: 'DISPONIBLE' as const,
    tipo: TIPOS[1],
  }));
}

function pagina(numero: number, total = 120, cuantas = 20): PaginaUnidades {
  return { number: numero, totalElements: total, content: unidades(cuantas, numero * 20 + 1) };
}

describe('Catalogo (pantalla)', () => {
  let fixture: ComponentFixture<Catalogo>;
  let http: HttpTestingController;
  const urlUnidades = `${environment.apiUrl}/unidades`;
  const urlTipos = `${environment.apiUrl}/tipos-equipo`;

  const html = () => fixture.nativeElement as HTMLElement;
  const boton = (texto: string) => {
    const encontrado = Array.from(html().querySelectorAll('button')).find((b) =>
      b.textContent?.includes(texto),
    );
    if (!encontrado) throw new Error(`No hay un botón con el texto "${texto}"`);
    return encontrado as HTMLButtonElement;
  };
  const filas = () => html().querySelectorAll('tbody tr');
  const peticionUnidades = () => http.expectOne((r) => r.url === urlUnidades);

  // Espera a que Angular termine de pintar lo que cambió en las señales.
  async function pintar() {
    await fixture.whenStable();
    fixture.detectChanges();
  }

  // Abre la pantalla y responde la lista de tipos; la primera página queda pendiente.
  function iniciar() {
    fixture = TestBed.createComponent(Catalogo);
    fixture.detectChanges();                                  // ngOnInit
    http.expectOne(urlTipos).flush(TIPOS);
  }

  // Abre la pantalla y responde también la primera página.
  async function abrirPantalla(primera: PaginaUnidades = pagina(0)) {
    iniciar();
    peticionUnidades().flush(primera);
    await pintar();
  }

  // Pulsa "Siguiente" y responde con la página indicada.
  async function irA(numero: number) {
    boton('Siguiente').click();
    peticionUnidades().flush(pagina(numero));
    await pintar();
  }

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [Catalogo],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    vi.useRealTimers();
    http.verify();
  });

  it('CA-1: con 120 unidades muestra 20 filas con tipo, número y estado', async () => {
    await abrirPantalla();

    expect(filas().length).toBe(20);
    const primera = filas()[0].textContent!;
    expect(primera).toContain('Multímetro');
    expect(primera).toContain('MULT-001');
    expect(primera).toContain('Disponible');
  });

  it('muestra los estados con texto legible y no con el valor técnico', async () => {
    const enRevision: Unidad = { ...unidades(1)[0], estado: 'EN_REVISION' };
    await abrirPantalla({ number: 0, totalElements: 1, content: [enRevision] });

    expect(filas()[0].textContent).toContain('En revisión');
    expect(html().textContent).not.toContain('EN_REVISION');
  });

  it('CA-2: "siguiente" pide la página siguiente y el indicador dice página 2 de 6', async () => {
    await abrirPantalla();
    expect(html().textContent).toContain('Página 1 de 6');

    boton('Siguiente').click();
    const peticion = peticionUnidades();
    expect(peticion.request.params.get('page')).toBe('1');
    peticion.flush(pagina(1));
    await pintar();

    expect(html().textContent).toContain('Página 2 de 6');
    expect(filas()[0].textContent).toContain('MULT-021');
  });

  it('CA-3: en la primera página "anterior" está deshabilitado y en la última, "siguiente"', async () => {
    await abrirPantalla();
    expect(boton('Anterior').disabled).toBe(true);
    expect(boton('Siguiente').disabled).toBe(false);

    await irA(5);                                             // página 6 de 6

    expect(boton('Siguiente').disabled).toBe(true);
    expect(boton('Anterior').disabled).toBe(false);
  });

  it('CA-4: mientras la API no responde muestra "Cargando", sin lista y con los botones bloqueados', async () => {
    iniciar();
    await pintar();

    expect(html().textContent).toContain('Cargando');
    expect(filas().length).toBe(0);
    expect(boton('Anterior').disabled).toBe(true);
    expect(boton('Siguiente').disabled).toBe(true);

    peticionUnidades().flush(pagina(0));                      // cierra la petición pendiente
  });

  it('CA-5: si la API falla muestra un mensaje legible y "Reintentar" vuelve a pedir', async () => {
    iniciar();
    peticionUnidades().error(new ProgressEvent('error'), { status: 0 });
    await pintar();

    expect(html().querySelector('[role="alert"]')?.textContent).toContain('No hay conexión');
    expect(filas().length).toBe(0);

    boton('Reintentar').click();
    peticionUnidades().flush(pagina(0));
    await pintar();
    expect(filas().length).toBe(20);
    expect(html().querySelector('[role="alert"]')?.textContent?.trim()).toBe('');
  });

  it('CA-5: "Reintentar" repite la página que falló, no la primera', async () => {
    await abrirPantalla();
    await irA(1);
    await irA(2);

    boton('Siguiente').click();                               // pide la página índice 3
    peticionUnidades().error(new ProgressEvent('error'), { status: 0 });
    await pintar();

    boton('Reintentar').click();
    const reintento = peticionUnidades();
    expect(reintento.request.params.get('page')).toBe('3');   // no vuelve a la 0
    reintento.flush(pagina(3));
    await pintar();

    expect(html().textContent).toContain('Página 4 de 6');
  });

  it('CA-7: elegir un tipo y "solo disponibles" pide con esos filtros y vuelve a la página 1', async () => {
    await abrirPantalla();
    await irA(1);
    expect(html().textContent).toContain('Página 2 de 6');

    const select = html().querySelector('select')!;
    select.value = '2';
    select.dispatchEvent(new Event('change'));
    let peticion = peticionUnidades();
    expect(peticion.request.params.get('tipoId')).toBe('2');
    expect(peticion.request.params.get('page')).toBe('0');
    peticion.flush({ number: 0, totalElements: 40, content: unidades(20) });
    await pintar();

    const check = html().querySelector('input[type=checkbox]') as HTMLInputElement;
    check.checked = true;
    check.dispatchEvent(new Event('change'));
    peticion = peticionUnidades();
    expect(peticion.request.params.get('tipoId')).toBe('2');
    expect(peticion.request.params.get('estado')).toBe('DISPONIBLE');
    expect(peticion.request.params.get('page')).toBe('0');
    peticion.flush({ number: 0, totalElements: 20, content: unidades(20) });
    await pintar();

    expect(html().textContent).toContain('Página 1 de 1');
    expect(filas().length).toBe(20);
  });

  it('CA-8: escribir "multi" hace UNA sola petición, tras la pausa, con ese nombre', async () => {
    await abrirPantalla();
    vi.useFakeTimers();

    const caja = html().querySelector('input[type=search]') as HTMLInputElement;
    for (const texto of ['m', 'mu', 'mul', 'mult', 'multi']) {
      caja.value = texto;
      caja.dispatchEvent(new Event('input'));
      vi.advanceTimersByTime(100);                            // menos de 300 ms entre teclas
    }
    vi.advanceTimersByTime(199);                              // 299 ms desde la última tecla
    http.expectNone((r) => r.url === urlUnidades);            // todavía no pidió nada

    vi.advanceTimersByTime(1);                                // se cumplen los 300 ms
    const peticion = peticionUnidades();                      // exactamente una
    expect(peticion.request.params.get('nombre')).toBe('multi');
    expect(peticion.request.params.get('page')).toBe('0');
    peticion.flush({ number: 0, totalElements: 20, content: unidades(20) });
  });

  it('CA-8: un espacio al final no repite la búsqueda', async () => {
    await abrirPantalla();
    vi.useFakeTimers();

    const caja = html().querySelector('input[type=search]') as HTMLInputElement;
    caja.value = 'multi';
    caja.dispatchEvent(new Event('input'));
    vi.advanceTimersByTime(300);
    peticionUnidades().flush({ number: 0, totalElements: 20, content: unidades(20) });

    caja.value = 'multi ';                                    // mismo texto + un espacio
    caja.dispatchEvent(new Event('input'));
    vi.advanceTimersByTime(300);
    http.expectNone((r) => r.url === urlUnidades);
  });

  it('CA-8: escribir solo espacios no lanza ninguna petición', async () => {
    await abrirPantalla();
    vi.useFakeTimers();

    const caja = html().querySelector('input[type=search]') as HTMLInputElement;
    caja.value = '   ';
    caja.dispatchEvent(new Event('input'));
    vi.advanceTimersByTime(300);

    http.expectNone((r) => r.url === urlUnidades);            // el filtro no cambió: nada que pedir
  });

  it('CA-3: "Anterior" vuelve a la página anterior', async () => {
    await abrirPantalla();
    await irA(1);
    expect(html().textContent).toContain('Página 2 de 6');

    boton('Anterior').click();
    const peticion = peticionUnidades();
    expect(peticion.request.params.get('page')).toBe('0');
    peticion.flush(pagina(0));
    await pintar();

    expect(html().textContent).toContain('Página 1 de 6');
    expect(boton('Anterior').disabled).toBe(true);
  });

  it('CA-7: volver a "Todos" quita el filtro de tipo', async () => {
    await abrirPantalla();
    const select = html().querySelector('select')!;

    select.value = '2';
    select.dispatchEvent(new Event('change'));
    expect(peticionUnidades().request.params.get('tipoId')).toBe('2');
    // la petición anterior se cancela al pedir otra, así que se responde la nueva
    select.value = '';
    select.dispatchEvent(new Event('change'));
    const peticion = peticionUnidades();

    expect(peticion.request.params.has('tipoId')).toBe(false);
    expect(peticion.request.params.get('page')).toBe('0');
    peticion.flush(pagina(0));
  });

  it('al volver a entrar a la pantalla los filtros guardados en el servicio se limpian', async () => {
    await abrirPantalla();
    const select = html().querySelector('select')!;
    select.value = '2';
    select.dispatchEvent(new Event('change'));
    peticionUnidades().flush({ number: 0, totalElements: 40, content: unidades(20) });
    await pintar();
    fixture.destroy();                                        // el usuario sale del catálogo

    iniciar();                                                // y vuelve a entrar
    const peticion = peticionUnidades();

    expect(peticion.request.params.has('tipoId')).toBe(false);
    expect(peticion.request.params.has('estado')).toBe(false);
    expect(peticion.request.params.has('nombre')).toBe(false);
    peticion.flush(pagina(0));
  });

  it('un estado que el cliente no conoce se muestra tal cual y no en blanco', async () => {
    const desconocida = { ...unidades(1)[0], estado: 'RETIRADA' as never };
    await abrirPantalla({ number: 0, totalElements: 1, content: [desconocida] });

    expect(filas()[0].textContent).toContain('RETIRADA');
  });

  it('CA-10: sin resultados muestra un mensaje y los dos botones quedan deshabilitados', async () => {
    await abrirPantalla({ number: 0, totalElements: 0, content: [] });

    expect(html().textContent).toContain('No hay equipos que coincidan');
    expect(filas().length).toBe(0);
    expect(boton('Anterior').disabled).toBe(true);
    expect(boton('Siguiente').disabled).toBe(true);
  });

  it('accesibilidad: la tabla tiene título, las columnas tienen alcance y la paginación está nombrada', async () => {
    await abrirPantalla();

    expect(html().querySelector('caption')?.textContent).toContain('Equipos del catálogo');
    expect(html().querySelectorAll('th[scope="col"]').length).toBe(3);
    expect(html().querySelector('nav')?.getAttribute('aria-label')).toBe('Paginación del catálogo');
    expect(html().querySelector('[role="status"]')).not.toBeNull();
  });
});
