package com.boutique.pos.dto;

import java.time.LocalDateTime;

/**
 * Lo único que devuelve la consulta pública SOLO por teléfono ("¿No tienes tu folio?"):
 * cuándo se pidió y en qué estado está. Sin folio, productos ni montos: con el folio y el
 * teléfono se abre el detalle (y se puede cancelar), así que darlo aquí permitiría a
 * cualquiera que sepa un teléfono ver lo que esa persona apartó y cuánto pagó. El cliente
 * llama a la tienda, da la fecha y ahí le dan su folio.
 */
public record PublicApartadoBrief(LocalDateTime requestedAt, String status) {
}
