
package com.tedesoft.prestamosservice.delivery.rest;

import com.tedesoft.prestamosservice.domain.clients.IAlmacenamientoFotos;
import com.tedesoft.prestamosservice.domain.entities.Prestamo;
import com.tedesoft.prestamosservice.domain.exception.RecursoNoEncontradoException;
import com.tedesoft.prestamosservice.domain.services.IPrestamoService;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import com.tedesoft.prestamosservice.domain.exception.FotoInvalidaException;
import com.tedesoft.prestamosservice.domain.exception.DependenciaNoDisponibleException;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;

import static org.mockito.AdditionalMatchers.aryEq;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.tedesoft.prestamosservice.delivery.security.ConSeguridadDePrueba;

@ConSeguridadDePrueba
@WebMvcTest(PrestamoRestController.class)
class PrestamoFotosRestTest {

    private static final String RUTA =
            "/api/v1/prestamos-service/prestamos";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private IPrestamoService prestamoService;

    private Prestamo crearPrestamo() {
        Prestamo prestamo = Prestamo.solicitar(
                14L,
                2L,
                "ana",
                7,
                LocalDate.of(2026, 11, 13)
        );

        ReflectionTestUtils.setField(
                prestamo, "id", 1L
        );

        return prestamo;
    }

    private byte[] crearImagen(String formato)
            throws IOException {

        BufferedImage imagen = new BufferedImage(
                2, 2, BufferedImage.TYPE_INT_RGB
        );

        ByteArrayOutputStream salida =
                new ByteArrayOutputStream();

        if (!ImageIO.write(imagen, formato, salida)) {
            throw new IOException(
                    "No se pudo generar la imagen de prueba"
            );
        }

        return salida.toByteArray();
    }

    @Test
    void entregarConFoto_debeResponder200()
            throws Exception {

        byte[] contenido = crearImagen("png");

        MockMultipartFile foto = new MockMultipartFile(
                "foto",
                "entrega.png",
                "image/png",
                contenido
        );

        Prestamo prestamo = crearPrestamo();
        prestamo.entregar();
        prestamo.registrarFotoEntrega(
                "prestamos/fotos/entrega-1"
        );

        when(prestamoService.entregarConFoto(
                eq(1L),
                any(byte[].class),
                eq("image/png")
        )).thenReturn(prestamo);

        mockMvc.perform(
                        multipart(RUTA + "/1/entregar")
                                .file(foto)
                                .with(request -> {
                                    request.setMethod("PATCH");
                                    return request;
                                })
                                .accept(MediaType.APPLICATION_JSON)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.estado")
                        .value("ENTREGADO"));

        verify(prestamoService).entregarConFoto(
                eq(1L),
                aryEq(contenido),
                eq("image/png")
        );

        verify(prestamoService, never())
                .entregar(1L);
    }

    @Test
    void recibirConFoto_debeResponder200()
            throws Exception {

        byte[] contenido = crearImagen("jpg");

        MockMultipartFile foto = new MockMultipartFile(
                "foto",
                "devolucion.jpg",
                "image/jpeg",
                contenido
        );

        Prestamo prestamo = crearPrestamo();
        prestamo.entregar();
        prestamo.recibir();
        prestamo.registrarFotoDevolucion(
                "prestamos/fotos/devolucion-1"
        );

        when(prestamoService.recibirConFoto(
                eq(1L),
                any(byte[].class),
                eq("image/jpeg")
        )).thenReturn(prestamo);

        mockMvc.perform(
                        multipart(RUTA + "/1/recibir")
                                .file(foto)
                                .with(request -> {
                                    request.setMethod("PATCH");
                                    return request;
                                })
                                .accept(MediaType.APPLICATION_JSON)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.estado")
                        .value("DEVUELTO"));

        verify(prestamoService).recibirConFoto(
                eq(1L),
                aryEq(contenido),
                eq("image/jpeg")
        );

        verify(prestamoService, never())
                .recibir(1L);
    }


    @Test
    void entregarConFoto_sinCampoFoto_debeResponder400()
            throws Exception {

        MockMultipartFile archivo = new MockMultipartFile(
                "archivo",
                "entrega.png",
                "image/png",
                crearImagen("png")
        );

        mockMvc.perform(
                        multipart(RUTA + "/1/entregar")
                                .file(archivo)
                                .with(request -> {
                                    request.setMethod("PATCH");
                                    return request;
                                })
                                .accept(MediaType.APPLICATION_JSON)
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(prestamoService);
    }

    @Test
    void recibirConFoto_sinCampoFoto_debeResponder400()
            throws Exception {

        MockMultipartFile archivo = new MockMultipartFile(
                "archivo",
                "devolucion.jpg",
                "image/jpeg",
                crearImagen("jpg")
        );

        mockMvc.perform(
                        multipart(RUTA + "/1/recibir")
                                .file(archivo)
                                .with(request -> {
                                    request.setMethod("PATCH");
                                    return request;
                                })
                                .accept(MediaType.APPLICATION_JSON)
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(prestamoService);
    }


    @Test
    void entregarConFoto_imagenInvalida_debeResponder400()
            throws Exception {

        byte[] contenido = {1, 2, 3, 4};

        MockMultipartFile foto = new MockMultipartFile(
                "foto",
                "imagen-falsa.png",
                "image/png",
                contenido
        );

        when(prestamoService.entregarConFoto(
                eq(1L),
                any(byte[].class),
                eq("image/png")
        )).thenThrow(
                new FotoInvalidaException(
                        "La fotografía no es válida"
                )
        );

        mockMvc.perform(
                        multipart(RUTA + "/1/entregar")
                                .file(foto)
                                .with(request -> {
                                    request.setMethod("PATCH");
                                    return request;
                                })
                                .accept(MediaType.APPLICATION_JSON)
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        verify(prestamoService).entregarConFoto(
                eq(1L),
                aryEq(contenido),
                eq("image/png")
        );
    }

    @Test
    void entregarConFoto_s3Deshabilitado_debeResponder503()
            throws Exception {

        MockMultipartFile foto = new MockMultipartFile(
                "foto",
                "entrega.png",
                "image/png",
                crearImagen("png")
        );

        when(prestamoService.entregarConFoto(
                eq(1L),
                any(byte[].class),
                eq("image/png")
        )).thenThrow(
                new DependenciaNoDisponibleException(
                        "El almacenamiento S3 no está configurado"
                )
        );

        mockMvc.perform(
                        multipart(RUTA + "/1/entregar")
                                .file(foto)
                                .with(request -> {
                                    request.setMethod("PATCH");
                                    return request;
                                })
                                .accept(MediaType.APPLICATION_JSON)
                )
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503));
    }

    @Test
    void recibirConFoto_errorS3_debeResponder503()
            throws Exception {

        MockMultipartFile foto = new MockMultipartFile(
                "foto",
                "devolucion.jpg",
                "image/jpeg",
                crearImagen("jpg")
        );

        when(prestamoService.recibirConFoto(
                eq(1L),
                any(byte[].class),
                eq("image/jpeg")
        )).thenThrow(
                new DependenciaNoDisponibleException(
                        "El almacenamiento de fotografías no está disponible. Intenta más tarde."
                )
        );

        mockMvc.perform(
                        multipart(RUTA + "/1/recibir")
                                .file(foto)
                                .with(request -> {
                                    request.setMethod("PATCH");
                                    return request;
                                })
                                .accept(MediaType.APPLICATION_JSON)
                )
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503));
    }

    // ------------------------------------------------------------------ CA-5: la foto es obligatoria

    @Test
    void entregarSinCuerpo_debeResponder400SinTocarElPrestamo()
            throws Exception {

        mockMvc.perform(
                        patch(RUTA + "/1/entregar")
                                .accept(MediaType.APPLICATION_JSON)
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.mensaje")
                        .value("La fotografía es obligatoria"));

        verifyNoInteractions(prestamoService);
    }

    @Test
    void recibirSinCuerpo_debeResponder400SinTocarElPrestamo()
            throws Exception {

        mockMvc.perform(
                        patch(RUTA + "/1/recibir")
                                .accept(MediaType.APPLICATION_JSON)
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje")
                        .value("La fotografía es obligatoria"));

        verifyNoInteractions(prestamoService);
    }

    @Test
    void entregarConJsonEnVezDeFoto_debeResponder400()
            throws Exception {

        mockMvc.perform(
                        patch(RUTA + "/1/entregar")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}")
                                .accept(MediaType.APPLICATION_JSON)
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje")
                        .value("La fotografía es obligatoria"));

        verifyNoInteractions(prestamoService);
    }

    @Test
    void multipartSinLaParteFoto_debeResponder400ConElMotivo()
            throws Exception {

        MockMultipartFile otraParte = new MockMultipartFile(
                "otra", "x.txt", "text/plain", new byte[]{1}
        );

        mockMvc.perform(
                        multipart(RUTA + "/1/recibir")
                                .file(otraParte)
                                .with(request -> {
                                    request.setMethod("PATCH");
                                    return request;
                                })
                                .accept(MediaType.APPLICATION_JSON)
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje")
                        .value("La fotografía es obligatoria"));

        verifyNoInteractions(prestamoService);
    }

    // ------------------------------------------------------------------ CA-3: pedir las fotos

    @Test
    void fotoEntrega_debeResponderLaImagenConSuTipo() throws Exception {
        byte[] contenido = crearImagen("png");
        when(prestamoService.obtenerFotoEntrega(1L))
                .thenReturn(new IAlmacenamientoFotos.Foto(contenido, "image/png"));

        mockMvc.perform(get(RUTA + "/1/foto-entrega"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG))
                .andExpect(content().bytes(contenido))
                .andExpect(header().string("Cache-Control", "no-store"));
    }

    @Test
    void fotoDevolucion_debeResponderLaImagenJpeg() throws Exception {
        byte[] contenido = crearImagen("jpg");
        when(prestamoService.obtenerFotoDevolucion(1L))
                .thenReturn(new IAlmacenamientoFotos.Foto(contenido, "image/jpeg"));

        mockMvc.perform(get(RUTA + "/1/foto-devolucion").accept(MediaType.ALL))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_JPEG))
                .andExpect(content().bytes(contenido));
    }

    @Test
    void foto_sinAccept_ni_json_tambienResponde() throws Exception {
        // Angular manda "application/json, text/plain, */*": debe recibir la imagen igualmente
        when(prestamoService.obtenerFotoEntrega(1L))
                .thenReturn(new IAlmacenamientoFotos.Foto(new byte[]{1, 2}, "image/png"));

        mockMvc.perform(get(RUTA + "/1/foto-entrega")
                        .header("Accept", "application/json, text/plain, */*"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG));
    }

    @Test
    void foto_sinTipoGuardado_respondeOctetStream() throws Exception {
        when(prestamoService.obtenerFotoEntrega(1L))
                .thenReturn(new IAlmacenamientoFotos.Foto(new byte[]{1, 2}, null));

        mockMvc.perform(get(RUTA + "/1/foto-entrega"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_OCTET_STREAM));
    }

    @Test
    void foto_inexistente_respondeJson404AunqueSePidaUnaImagen() throws Exception {
        when(prestamoService.obtenerFotoEntrega(1L))
                .thenThrow(new RecursoNoEncontradoException(
                        "El préstamo 1 aún no tiene fotografía de entrega"));

        mockMvc.perform(get(RUTA + "/1/foto-entrega").accept("image/png"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.mensaje")
                        .value("El préstamo 1 aún no tiene fotografía de entrega"));
    }

    @Test
    void foto_conAlmacenamientoCaido_respondeJson503() throws Exception {
        when(prestamoService.obtenerFotoDevolucion(1L))
                .thenThrow(new DependenciaNoDisponibleException(
                        "El almacenamiento de fotografías no está disponible"));

        mockMvc.perform(get(RUTA + "/1/foto-devolucion").accept("image/*"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503));
    }

    @Test
    void foto_conIdNoNumerico_responde400() throws Exception {
        mockMvc.perform(get(RUTA + "/abc/foto-entrega"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(prestamoService);
    }

}
