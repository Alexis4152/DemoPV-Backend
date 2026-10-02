package com.boutique.pos.service;

import com.boutique.pos.model.Product;
import com.boutique.pos.model.ProductImage;
import com.boutique.pos.repository.ProductImageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Administra las fotos de un {@link Product} (galería, pensada sobre todo para exhibirlo
 * en la tienda pública de apartados). Un producto puede tener VARIAS fotos, no una sola —
 * se guardan bajo {@code app.uploads.dir}/products/{@code {productId}}/ con nombre único
 * (el guardado en disco en sí, incluyendo la validación de tipo/tamaño, lo delega en
 * {@link ImageStorageService}, compartido con {@link TiendaLogoService}).
 *
 * <p>El control de acceso (que el producto pertenezca a la tienda del actor) lo hace
 * quien llama a este servicio ({@code ProductController}, vía {@code ProductService.findById(id, actor)})
 * antes de invocar cualquiera de estos métodos — este servicio ya recibe el {@link
 * Product} validado, no vuelve a comprobar tienda.</p>
 */
@Service
@RequiredArgsConstructor
public class ProductImageService {

    private static final long MAX_SIZE_BYTES = 5L * 1024 * 1024; // 5 MB

    private final ProductImageRepository imageRepository;
    private final ImageStorageService imageStorageService;

    /** Fotos de un producto, portada primero. */
    public List<ProductImage> list(Long productId) {
        return imageRepository.findByProductIdOrderByIsPrimaryDescSortOrderAsc(productId);
    }

    /**
     * Sube una foto nueva para un producto. Si es la primera foto del producto, queda
     * marcada automáticamente como portada ({@code isPrimary}); si no, se agrega al final
     * de la galería.
     *
     * @param product producto ya validado (pertenece a la tienda del actor)
     * @param file archivo de imagen (PNG, JPEG o WEBP, máx. 5 MB)
     * @return la foto recién guardada
     * @throws IllegalArgumentException si el archivo viene vacío, no es de un tipo
     *         permitido o excede el tamaño máximo
     * @throws RuntimeException si falla la escritura del archivo a disco
     */
    @Transactional
    public ProductImage upload(Product product, MultipartFile file) {
        List<ProductImage> existing = imageRepository.findByProductId(product.getId());
        String path = imageStorageService.store(file, "products/" + product.getId(), "", MAX_SIZE_BYTES);

        ProductImage image = new ProductImage();
        image.setProduct(product);
        image.setPath(path);
        image.setIsPrimary(existing.isEmpty());
        image.setSortOrder(existing.size());
        return imageRepository.save(image);
    }

    /**
     * Marca una foto como portada del producto (y desmarca cualquier otra que lo fuera).
     *
     * @param product producto ya validado (pertenece a la tienda del actor)
     * @param imageId id de la foto a marcar como portada
     * @throws IllegalArgumentException si la foto no existe o no pertenece a este producto
     */
    @Transactional
    public void setPrimary(Product product, Long imageId) {
        List<ProductImage> images = imageRepository.findByProductId(product.getId());
        boolean found = false;
        for (ProductImage img : images) {
            boolean isTarget = img.getId().equals(imageId);
            img.setIsPrimary(isTarget);
            if (isTarget) found = true;
            imageRepository.save(img);
        }
        if (!found) throw new IllegalArgumentException("Imagen no encontrada: " + imageId);
    }

    /**
     * Borra una foto (archivo en disco best-effort + registro). Si era la portada y
     * quedan otras fotos, la primera restante pasa a ser la nueva portada automáticamente
     * (nunca se deja al producto sin portada mientras tenga al menos una foto).
     *
     * @param product producto ya validado (pertenece a la tienda del actor)
     * @param imageId id de la foto a borrar
     * @throws IllegalArgumentException si la foto no existe o no pertenece a este producto
     */
    @Transactional
    public void delete(Product product, Long imageId) {
        ProductImage image = imageRepository.findByProductId(product.getId()).stream()
                .filter(i -> i.getId().equals(imageId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Imagen no encontrada: " + imageId));

        boolean wasPrimary = Boolean.TRUE.equals(image.getIsPrimary());
        imageStorageService.delete(image.getPath());
        imageRepository.delete(image);

        if (wasPrimary) {
            imageRepository.findByProductIdOrderByIsPrimaryDescSortOrderAsc(product.getId()).stream()
                    .findFirst()
                    .ifPresent(next -> {
                        next.setIsPrimary(true);
                        imageRepository.save(next);
                    });
        }
    }
}
