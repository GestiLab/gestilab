import { TestBed, ComponentFixture } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Retrasos } from './retrasos';
import { environment } from '../../../environments/environment';
import { Retraso } from '../../models/retraso';

// Datos de ejemplo: ninguna prueba toca la API real.
const RETRASOS: Retraso[] = [
  { prestamoId: 7, unidadId: 12, solicitanteId: 'sub-ana', fechaDevolucionPrevista: '2026-11-20', diasRetraso: 6 },
  { prestamoId: 9, unidadId: 31, solicitanteId: 'sub-luis', fechaDevolucionPrevista: '2026-11-10', diasRetraso: 16 },
];

describe('Retrasos (pantalla)', () => {
  let fixture: ComponentFixture<Retrasos>;
  let http: HttpTestingController;
  const url = `${environment.prestamosApiUrl}/prestamos/retrasos`;

  const html = () => fixture.nativeElement as HTMLElement;
  const filas = () => Array.from(html().querySelectorAll('tbody tr'));
  const celdas = (fila: Element) => Array.from(fila.querySelectorAll('td')).map((c) => c.textContent?.trim());
  const boton = (texto: string) =>
    Array.from(html().querySelectorAll('button')).find((b) => b.textContent?.includes(texto));

  // Espera a que Angular termine de pintar lo que cambió en las señales.
  async function pintar() {
    await fixture.whenStable();
    fixture.detectChanges();
  }

  // Abre la pantalla; la petición queda pendiente.
  function abrir() {
    fixture = TestBed.createComponent(Retrasos);
    fixture.detectChanges();                                  // ngOnInit
  }

  // Abre la pantalla y responde la petición.
  async function abrirConRespuesta(lista: Retraso[]) {
    abrir();
    http.expectOne(url).flush(lista);
    await pintar();
  }

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [Retrasos],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('SCRUM-38 CA-1: muestra una fila por préstamo con unidad, persona, fecha y días de retraso', async () => {
    await abrirConRespuesta(RETRASOS);

    expect(filas().length).toBe(2);
    expect(celdas(filas()[0])).toEqual(['12', 'sub-ana', '20/11/2026', '6']);
    expect(celdas(filas()[1])).toEqual(['31', 'sub-luis', '10/11/2026', '16']);
    expect(html().querySelector('[role="status"]')?.textContent).toContain('Hay 2 préstamos retrasados');
  });

  it('SCRUM-38 CA-1: la tabla tiene encabezados de columna', async () => {
    await abrirConRespuesta(RETRASOS);

    const encabezados = Array.from(html().querySelectorAll('th[scope="col"]')).map((t) => t.textContent?.trim());
    expect(encabezados).toEqual(['Unidad', 'Persona', 'Fecha de devolución', 'Días de retraso']);
  });

  it('SCRUM-38 CA-2: sin retrasos dice con claridad que no hay y no dibuja la tabla', async () => {
    await abrirConRespuesta([]);

    expect(html().querySelector('[role="status"]')?.textContent).toContain('No hay préstamos retrasados.');
    expect(html().querySelector('table')).toBeNull();
  });

  it('SCRUM-38 CA-3: mientras espera muestra "Cargando" y ninguna tabla', async () => {
    abrir();
    await pintar();

    expect(html().querySelector('[role="status"]')?.textContent).toContain('Cargando retrasos');
    expect(html().querySelector('table')).toBeNull();
    http.expectOne(url).flush(RETRASOS);
  });

  it('SCRUM-38 CA-3: si el servicio falla muestra un mensaje legible y el botón Reintentar', async () => {
    abrir();
    http.expectOne(url).error(new ProgressEvent('error'), { status: 0 });
    await pintar();

    expect(html().querySelector('[role="alert"]')?.textContent).toContain('No hay conexión con el servicio de préstamos');
    expect(boton('Reintentar')).toBeDefined();
    expect(html().querySelector('table')).toBeNull();
  });

  it('SCRUM-38 CA-3: "Reintentar" vuelve a pedir y, si responde bien, muestra la lista', async () => {
    abrir();
    http.expectOne(url).error(new ProgressEvent('error'), { status: 0 });
    await pintar();

    boton('Reintentar')!.click();
    http.expectOne(url).flush(RETRASOS);
    await pintar();

    expect(filas().length).toBe(2);
    expect(boton('Reintentar')).toBeUndefined();
  });

  it('el aviso usa el singular cuando hay un solo préstamo retrasado', async () => {
    await abrirConRespuesta([RETRASOS[0]]);

    expect(html().querySelector('[role="status"]')?.textContent).toContain('Hay 1 préstamo retrasado');
    expect(html().querySelector('[role="status"]')?.textContent).not.toContain('préstamos');
  });

  it('al pulsar Reintentar el foco pasa al encabezado y no se pierde', async () => {
    abrir();
    http.expectOne(url).error(new ProgressEvent('error'), { status: 0 });
    await pintar();

    boton('Reintentar')!.click();
    await pintar();

    expect(document.activeElement).toBe(html().querySelector('h2'));
    http.expectOne(url).flush(RETRASOS);
  });

  it('SCRUM-38 CA-4: los días se muestran tal como llegan, sin importar la fecha del navegador', async () => {
    vi.useFakeTimers({ toFake: ['Date'] });
    vi.setSystemTime(new Date('2035-01-01T12:00:00Z'));
    try {
      await abrirConRespuesta(RETRASOS);
      expect(celdas(filas()[0])[3]).toBe('6');
    } finally {
      vi.useRealTimers();
    }
  });
});
