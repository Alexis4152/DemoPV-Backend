package com.boutique.pos.service;

import com.boutique.pos.dto.CategoryRequest;
import com.boutique.pos.model.Category;
import com.boutique.pos.model.Tienda;
import com.boutique.pos.model.User;
import com.boutique.pos.repository.CategoryRepository;
import com.boutique.pos.security.TenantScope;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * CRUD de categorías de producto, con aislamiento por tienda (multi-tenancy).
 *
 * <p>Cada categoría pertenece a una sola tienda. Las consultas se filtran mediante
 * {@link TenantScope} para que un usuario normal solo vea/edite las categorías de su
 * propia tienda, mientras que un SUPER_ADMIN (sin tienda asignada) puede ver todas.</p>
 */
@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final TenantScope tenantScope;

    /**
     * Lista las categorías visibles para el actor: las de su tienda, o todas si es
     * SUPER_ADMIN.
     *
     * @param actor usuario que realiza la consulta
     * @return categorías dentro del alcance (scope) del actor
     */
    public List<Category> findAll(User actor) {
        return categoryRepository.findAllForTienda(tenantScope.scopeId(actor));
    }

    /**
     * Busca una categoría por id sin validar a qué tienda pertenece. Uso interno /
     * administrativo; para flujos con control de acceso usar {@link #findById(Long, User)}.
     *
     * @param id id de la categoría
     * @return la categoría encontrada
     * @throws IllegalArgumentException si no existe
     */
    public Category findById(Long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Categoría no encontrada: " + id));
    }

    /**
     * Busca una categoría por id validando que pertenezca a la tienda del actor.
     *
     * <p>Si el actor es SUPER_ADMIN (scope null) no se aplica restricción. Para evitar
     * filtrar la existencia de categorías de otras tiendas, cuando la validación falla se
     * lanza el mismo error de "no encontrada" que si el id no existiera.</p>
     *
     * @param id id de la categoría
     * @param actor usuario que realiza la consulta
     * @return la categoría encontrada, perteneciente al alcance del actor
     * @throws IllegalArgumentException si no existe o no pertenece a la tienda del actor
     */
    public Category findById(Long id, User actor) {
        Category c = findById(id);
        Long scope = tenantScope.scopeId(actor);
        if (scope != null && (c.getTienda() == null || !scope.equals(c.getTienda().getId()))) {
            throw new IllegalArgumentException("Categoría no encontrada: " + id);
        }
        return c;
    }

    /**
     * Crea una categoría nueva, asignada automáticamente a la tienda del actor (la que
     * esté actuando, si es SUPER_ADMIN — ver {@link TenantScope#tiendaForWrite}).
     *
     * @param req datos de la categoría (nombre, descripción)
     * @param actor usuario que la crea; queda registrado como {@code createdBy}
     * @return la categoría creada
     * @throws IllegalStateException si es SUPER_ADMIN sin ninguna tienda elegida para actuar
     */
    public Category create(CategoryRequest req, User actor) {
        Tienda tienda = tenantScope.tiendaForWrite(actor);
        if (tienda == null && tenantScope.isPlatformActor(actor)) {
            throw new IllegalStateException("Elige una tienda para poder crear una categoría");
        }
        Category cat = new Category();
        cat.setName(req.getName());
        cat.setDescription(req.getDescription());
        cat.setTienda(tienda);
        cat.setCreatedBy(actor);
        return categoryRepository.save(cat);
    }

    /**
     * Actualiza nombre y descripción de una categoría existente.
     *
     * @param id id de la categoría a modificar
     * @param req nuevos valores (nombre, descripción)
     * @param actor usuario que hace el cambio; debe tener acceso a la tienda de la
     *              categoría (validado por {@link #findById(Long, User)}); queda
     *              registrado como {@code updatedBy}
     * @return la categoría actualizada
     */
    public Category update(Long id, CategoryRequest req, User actor) {
        Category cat = findById(id, actor);
        cat.setName(req.getName());
        cat.setDescription(req.getDescription());
        cat.setUpdatedBy(actor);
        return categoryRepository.save(cat);
    }

    // antes borraba la fila; ahora es borrado suave (igual que products/users/tiendas)
    // para poder conservar quién y cuándo la eliminó.
    /**
     * Elimina (borrado suave) una categoría: marca {@code isActive=false} y registra
     * quién y cuándo la eliminó, sin borrar la fila de la base de datos.
     *
     * @param id id de la categoría a eliminar
     * @param actor usuario que elimina; debe tener acceso a la tienda de la categoría
     */
    public void delete(Long id, User actor) {
        Category cat = findById(id, actor);
        cat.setIsActive(false);
        cat.setDeletedBy(actor);
        cat.setDeletedAt(java.time.LocalDateTime.now());
        categoryRepository.save(cat);
    }
}
