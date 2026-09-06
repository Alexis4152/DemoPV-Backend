package com.boutique.pos.model;

/**
 * Catálogo de los módulos/pantallas de la aplicación sobre los que se puede otorgar
 * acceso mediante el sistema de roles (RBAC).
 * <p>
 * Cada {@link Role} guarda un {@code Set<AppSection>} con las secciones a las que da
 * acceso; el frontend usa esta lista para decidir qué menús/rutas mostrarle al usuario
 * autenticado y el backend la usa para autorizar las peticiones a cada módulo.
 */
public enum AppSection {
    /** Pantalla de inicio con indicadores/resumen general de la tienda. */
    DASHBOARD,
    /** Punto de venta: pantalla para capturar y cobrar ventas. */
    POS,
    /** Gestión de inventario: productos, categorías y movimientos de stock. */
    INVENTORY,
    /** Historial y consulta de ventas realizadas. */
    SALES,
    /** Apertura, cierre y consulta de cortes de caja. */
    CASH_CUTS,
    /** Reportes (ej. total de ventas por día) y exportación de información. */
    REPORTS,
    /** Administración de usuarios de la tienda. */
    USERS,
    /** Administración de roles y sus permisos por sección. */
    ROLES,
    /** Apartados: revisar, confirmar, completar y cancelar reservas de productos hechas
     *  desde la tienda pública de apartados. */
    APARTADOS
}
