package io.elasticmapper.binding;

import io.elasticmapper.executor.ESResponse;
import io.elasticmapper.result.Page;

import java.util.List;

/**
 * Base Mapper interface providing built-in CRUD operations.
 * User-defined Mapper interfaces extend this to gain automatic
 * insert/select/update/delete functionality without writing
 * any implementation code.
 *
 * <pre>{@code
 * public interface UserMapper extends BaseMapper<User> {
 *     // Custom query methods go here...
 * }
 * }</pre>
 *
 * @param <T> the entity type
 */
public interface BaseMapper<T> {

    // ── Insert ──

    /**
     * Inserts a single document. Uses the {@code @Id} field value
     * as the document ID, or auto-generates one if null.
     */
    ESResponse insert(T entity);

    /**
     * Inserts multiple documents in a single bulk request.
     */
    ESResponse insertBatch(List<T> entities);

    // ── Select ──

    /**
     * Retrieves a document by ID.
     * @return the entity, or null if not found
     */
    T selectById(String id);

    /**
     * Retrieves multiple documents by ID.
     */
    List<T> selectByIds(List<String> ids);

    /**
     * Retrieves all documents (capped at maxResultWindow, default 10000).
     */
    List<T> selectList();

    /**
     * Paginated query. Uses from/size pagination by default.
     */
    Page<T> selectPage(Page<T> page);

    // ── Update ──

    /**
     * Performs a full document update by ID.
     */
    ESResponse updateById(T entity);

    /**
     * Performs a partial update — only non-null fields are written.
     */
    ESResponse updatePartialById(T entity);

    // ── Delete ──

    /**
     * Deletes a document by ID.
     */
    ESResponse deleteById(String id);

    /**
     * Deletes multiple documents by ID.
     */
    ESResponse deleteByIds(List<String> ids);
}
