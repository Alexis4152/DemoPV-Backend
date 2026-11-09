# Changelog — DemoPV Backend

Cambios del backend. Los del portal están en el `CHANGELOG.md` de DemoPV-Frontend.
Los números entre paréntesis son las secciones de la matriz `Pruebas aprobacion DemoPV (version 4).xlsx`.

## [Sin liberar] — 2026-11-07

Cambios posteriores al commit `50bc75f` (2026-10-28). Todavía no tienen commit.

**Estado de validación:** de 312 casos en la matriz, 212 están OK, 99 pendientes, 1 no aplica y ninguno en falla. La sección 32 ya está validada; 34 y 35 van a medias, y 33, 36, 37, 38 y 39 aún no se validan en pantalla.

### Correcciones críticas

- **Ventas con total negativo (1):** se rechazan descuentos mayores al importe de la línea o al total de la venta, descuentos e impuestos negativos, y descuentos por encima del límite de la tienda. Una venta vieja con total negativo no se puede devolver.

### Correcciones graves

- **Cancelar y devolver (2):** una venta de un turno cerrado ya no se cancela, se devuelve. En efectivo, el dinero sale del corte abierto de quien devuelve; el corte original no cambia.
- **Anticipos desde Ventas (5):** un anticipo o abono no se puede cancelar ni devolver desde Ventas; se gestiona desde su apartado. Un cliente ya no puede auto-cancelar en línea un apartado con anticipo.
- **Cobro final de un apartado (6):** cancelar la venta final regresa el apartado a Activo, sin mover inventario ni perder el anticipo.
- **Concurrencia (21):** se bloquean los registros involucrados para evitar:
  - folios de venta duplicados (antes, error 500)
  - devoluciones o cancelaciones dobles, que sacaban el efectivo dos veces
  - abonos que rebasaban el saldo
  - dos ventas de saldo por el mismo apartado
- **Efectivo ya retirado (21):** no se puede cancelar una venta si la caja de ese turno ya no tiene ese efectivo.
- **Descuentos en apartados en línea (21):** al confirmar, se rechaza un descuento mayor al importe de la línea.
- **Apartado reactivado (21):** al cancelar el cobro de un apartado en línea vencido, recibe una vigencia nueva.

### Correcciones medias

- **Reportes (3):** las devoluciones restan en la fecha en que se hicieron, y se puede filtrar por "Devuelta".
- **Anticipo en la venta del saldo (7):** el anticipo se guarda en su propio renglón y ya no como descuento. Los reportes de descuentos dejan de incluirlo.
- **Devoluciones con tarjeta o transferencia (27):** se informan en el turno donde se hicieron, no en el de la venta. No cambian el efectivo esperado.
- **Venta por concepto (21, 31):** montos o cantidades fuera de rango dan un mensaje claro bajo el campo, no un error 500.
- **Correo tras gasto o retiro:** enviar el aviso ya no cancela el registro por una sesión de base de datos cerrada.

### Funcionalidades nuevas

**Apartados y anticipos**
- **Abonos (18):** pagos a cuenta con su venta, ticket y saldo después de cada pago.
- **Anticipo mínimo y retención (17, 23):** porcentaje por tienda, que se conserva en cada apartado. Por cambio de opinión la tienda retiene ese porcentaje; un producto defectuoso se devuelve completo y solo lo autoriza un administrador o supervisor.
- **Devolver un apartado completado (10):** devuelve primero la venta final, luego abonos y anticipo. Admite devoluciones parciales.
- **Inventario por revisar (11):** los apartados vencidos o cancelados por el cliente quedan pendientes de revisión; el stock no regresa solo.
- **Pago completo en un anticipo (34):** registra el mínimo como anticipo y el resto como saldo. Sin mínimo utilizable, divide 50/50. El apartado queda "Pagado · por entregar" hasta que se registra la entrega.
- **Sobre pedido (35):** cada producto del anticipo puede no descontarse del inventario. Al liquidar se indica qué líneas sí salen. Lo que nunca se descontó no regresa al cancelar o devolver.
- **Ticket digital:** endpoints nuevos `GET /api/sales/{id}/ticket-pdf` y `POST /api/sales/{id}/send-ticket`. Con acceso a Apartados también se puede imprimir el ticket.
- **Entrega de un apartado pagado (34, 35):** endpoint nuevo `POST /api/apartados/{id}/deliver`. Al entregar se indica qué productos sobre pedido salen del inventario. Si se cancela la liquidación o se devuelve el apartado, deja de estar por entregar. Cada entrega deja un renglón "Apartado entregado" por producto en Movimientos de inventario; si el producto no sale del stock en ese momento, el renglón es informativo y no cambia las piezas.
- **Entregas parciales (38):** se entrega por renglón, en una o varias visitas, y solo con el apartado pagado completo. `deliver` recibe `itemIds` (lo que se entrega ahora; sin él, todo lo pendiente) y `deductItemIds` (solo de lo que se entrega). Cada línea guarda `deliveredAt` y quién entregó (`deliveredByName`); el apartado sigue por entregar mientras falte algo y la tienda en línea lo muestra como `PAID_PARTIAL`. Al devolver, lo que nunca se entregó regresa solo al inventario y las entregas se conservan como historial. El reporte de inventario y el corte solo cuentan lo que falta. Al arrancar se copia la fecha de entrega a las líneas de los apartados ya entregados (`ApartadoItemDeliveryBackfill`).
- **Movimientos de apartados:** el origen dice cuándo pasó y el tipo si cambió el stock. "Apartado": confirmar (ahora con el folio, no el id) y anticipo; lo sobre pedido o por pedido deja un ajuste informativo. "Abono" (`APARTADO_INSTALLMENT`, nuevo): un ajuste informativo por producto con el monto y el saldo. "Apartado entregado": todo lo de la entrega, incluida la salida de lo sobre pedido al liquidar o al entregar. Liquidar o completar un apartado en línea cuenta como entrega de lo que falta: deja sus renglones y marca cada producto como entregado. Al arrancar se reclasifican las salidas sobre pedido existentes y se marcan como entregados los productos de apartados ya liquidados (fecha del cobro).
- **Qué pasó con cada producto:** el apartado expone `outcomeAt` y `outcomeByName` (revisión de inventario, devolución o cancelación) para mostrar cuándo y quién decidió.

**Inventario**
- **Qué pasó con el producto (9):** al cancelar o devolver se elige Regresa, Merma o No regresó, con sus movimientos de inventario.
- **Reporte diario de inventario (36):** hora configurable por tienda. Envía al supervisor un Excel con los productos, sus piezas y los anticipos sobre pedido por entregar: activos, o pagados que el cliente aún no recoge, sin importar la fecha. Sin supervisor, usa los destinatarios del corte.

**Caja**
- **Controles de cancelación y devolución (22):** un cajero solo cancela ventas de su turno. Cada tienda puede limitar el monto y la antigüedad de lo que un cajero devuelve.
- **Gastos y retiros al abrir el turno (32):** con la caja cerrada, un cajero puede registrar gastos y retiros de lo que quedó del día anterior. Estos movimientos avisan por correo a los destinatarios del corte.
- **Destino "Otro" en retiros (32):** el destino se escribe a mano.
- **Personas en movimientos de caja:** el listado incluye al supervisor de la tienda y excluye al super admin.
- **Devoluciones no efectivo en el turno (27):** endpoint nuevo `GET /api/cash-cuts/{id}/non-cash-refunds`.

**Tickets**
- **Ticket de anticipo (19, 28):** muestra productos, total del apartado, anticipo en negativo y saldo pendiente.
- **Saldo pendiente (19):** el ticket conserva el saldo que quedó después de ese pago.
- **Leyendas (17, 23, 28):** la regla de retención se imprime en el ticket y las ventas usan una leyenda sin productos concatenados.
- **Pago completo (34):** el ticket de liquidación muestra lo que entregó el cliente y su cambio.

### Excel del corte

- **Pestaña nueva "Cómo se calcula" (33):** explica cada dato del Resumen.
- **Resumen rediseñado (25, 33):** bloques Ventas, Devoluciones, Utilidad y Efectivo en caja, con una columna por turno y TOTAL DEL DÍA. Agrega Utilidad en efectivo y el bloque "Antes de abrir el turno".
- **Ventas (24, 33):** agrega FECHA VENTA, FECHA DEVOLUCION y devoluciones por método. Incluye las devoluciones de ventas de otros días.
- **Productos apartados (26):** hoja nueva, con un bloque por apartado.
- **Folio (33):** `FOLIO_VENTA` usa el folio de la tienda, no el ID interno.

### Despliegue

- Respaldar la base antes de desplegar.
- `ddl-auto=update` agrega las columnas nuevas, entre ellas `apartado_items.on_order`, `cash_movements.destination_other`, `tiendas.inventory_report_time` y `tiendas.last_inventory_report_date`, `apartados.pending_delivery`, `apartados.delivered_at` y `apartados.delivered_by_user_id`.
- Al arrancar se actualiza la restricción `cash_movements_destination_check` y corren los backfills de ventas de saldo, saldos de anticipos y movimientos de devolución.
- Los límites de devolución vienen vacíos: hay que configurarlos en Datos de la tienda.
- Hay correos nuevos. Conviene revisar los destinatarios del corte y el correo del supervisor antes de activar el reporte de inventario.

### Pendientes conocidos

**Fallas abiertas**
- Productos por pieza con cantidad decimal: cobran completo, pero descuentan solo la parte entera.
- El conteo ciego se puede deducir desde Ventas y los totales del turno.
- Mensajes genéricos del servidor en ventas.
- Ranking de productos y ventas por categoría todavía cuentan piezas devueltas.
- Al reactivar una oferta se pierde quién la desactivó.

**Por decidir**
- Si un anticipo mayor al mínimo se retiene completo o solo el porcentaje.
- Si los productos de apartados en línea salen en el Excel.
- El orden de las filas de la hoja Ventas.
- Si se renombran 49 registros viejos con leyenda y productos concatenados.
- Si la venta por concepto lleva unidad y permite $0.
- Si un supervisor puede autorizar desde el corte del cajero.
- En qué caja se muestra una devolución con tarjeta hecha en otra caja.
