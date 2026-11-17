
package com.tedesoft.prestamosservice.infrastructure.storage;

import com.tedesoft.prestamosservice.domain.clients.IAlmacenamientoFotos;
import com.tedesoft.prestamosservice.domain.exception.DependenciaNoDisponibleException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        name = "gestilab.fotos.s3.habilitado",
        havingValue = "false",
        matchIfMissing = true
)
public class AlmacenamientoFotosDeshabilitado
        implements IAlmacenamientoFotos {

    @Override
    public String guardar(byte[] contenido, String tipoMime) {
        throw new DependenciaNoDisponibleException(
                "El almacenamiento de fotografías S3 no está configurado"
        );
    }

    @Override
    public Foto recuperar(String clave) {
        throw new DependenciaNoDisponibleException(
                "El almacenamiento de fotografías S3 no está configurado"
        );
    }

    @Override
    public void eliminar(String clave) {
        throw new DependenciaNoDisponibleException(
                "El almacenamiento de fotografías S3 no está configurado"
        );
    }
}
