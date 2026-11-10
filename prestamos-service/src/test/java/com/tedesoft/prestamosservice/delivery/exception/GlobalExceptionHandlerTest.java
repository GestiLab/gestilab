package com.tedesoft.prestamosservice.delivery.exception;

import com.tedesoft.prestamosservice.domain.exception.DependenciaNoDisponibleException;
import com.tedesoft.prestamosservice.domain.exception.DependenciaRechazadaException;
import com.tedesoft.prestamosservice.domain.exception.RecursoNoEncontradoException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = GlobalExceptionHandlerTest.ProbeController.class)
@Import(GlobalExceptionHandlerTest.ProbeController.class)
class GlobalExceptionHandlerTest {

    @Autowired MockMvc mockMvc;

    @RestController
    static class ProbeController {
        record Entrada(@NotBlank(message = "no puede estar vacío") String nombre) { }

        @GetMapping("/probe/no-encontrado")
        void noEncontrado() { throw new RecursoNoEncontradoException("Unidad 99 no encontrada"); }

        @GetMapping("/probe/dependencia")
        void dependencia() {
            throw new DependenciaNoDisponibleException("El servicio de inventario no está disponible. Intenta más tarde.",
                    new java.net.SocketTimeoutException("Read timed out (detalle interno)"));
        }

        @GetMapping("/probe/rechazo")
        void rechazo() {
            throw new DependenciaRechazadaException("No se pudo consultar el servicio de inventario. Contacta al administrador.");
        }

        @PostMapping("/probe/validar")
        void validar(@Valid @RequestBody Entrada entrada) { }
    }

    @Test
    void excepcionDeNegocio_devuelve404ConFormatoComunSinStackTrace() throws Exception {
        mockMvc.perform(get("/probe/no-encontrado"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.mensaje").value("Unidad 99 no encontrada"))
                .andExpect(jsonPath("$.trace").doesNotExist());
    }

    @Test
    void cuerpoInvalido_devuelve400IndicandoElCampo() throws Exception {
        mockMvc.perform(post("/probe/validar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.nombre").value("no puede estar vacío"));
    }

    @Test
    void dependenciaNoDisponible_devuelve503ConFormatoComunSinFiltrarLaCausa() throws Exception {
        mockMvc.perform(get("/probe/dependencia"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.error").value("Service Unavailable"))
                .andExpect(jsonPath("$.mensaje").value("El servicio de inventario no está disponible. Intenta más tarde."))
                .andExpect(jsonPath("$.trace").doesNotExist())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("detalle interno"))));
    }

    @Test
    void dependenciaRechazada_devuelve502ConFormatoComun() throws Exception {
        mockMvc.perform(get("/probe/rechazo"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.status").value(502))
                .andExpect(jsonPath("$.error").value("Bad Gateway"))
                .andExpect(jsonPath("$.mensaje").value("No se pudo consultar el servicio de inventario. Contacta al administrador."))
                .andExpect(jsonPath("$.trace").doesNotExist());
    }
}
