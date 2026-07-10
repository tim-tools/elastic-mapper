package io.github.timtools.elasticmapper.example.mapper;

import io.github.timtools.elasticmapper.annotations.Delete;
import io.github.timtools.elasticmapper.annotations.Param;
import io.github.timtools.elasticmapper.annotations.Select;
import io.github.timtools.elasticmapper.annotations.Update;
import io.github.timtools.elasticmapper.binding.BaseMapper;
import io.github.timtools.elasticmapper.example.dto.StatusCounts;
import io.github.timtools.elasticmapper.example.entity.User;
import io.github.timtools.elasticmapper.result.AggR;

import java.util.List;

/**
 * User mapper — demonstrates both annotation-driven and XML-driven queries.
 *
 * <p>Built-in methods (inherited from {@link BaseMapper}):
 * <ul>
 *   <li>{@code insert(User)}</li>
 *   <li>{@code selectById(String)}</li>
 *   <li>{@code selectList()}</li>
 *   <li>{@code updateById(User)}</li>
 *   <li>{@code deleteById(String)}</li>
 * </ul>
 *
 * <p>Custom annotation-driven queries:
 * <ul>
 *   <li>{@link #findByUsername(String)}</li>
 *   <li>{@link #findByAgeRange(int, int)}</li>
 *   <li>{@link #updateStatus(String, String)}</li>
 *   <li>{@link #deleteByStatus(String)}</li>
 * </ul>
 *
 * <p>XML-driven queries (defined in {@code es-mapper/UserMapper.xml}):
 * <ul>
 *   <li>{@code searchUsers(String keyword, Integer minAge, String status)}</li>
 *   <li>{@code countByStatus(String status)}</li>
 * </ul>
 */
public interface UserMapper extends BaseMapper<User> {

    /**
     * Find users by exact username match (annotation-driven).
     * Equivalent to ES query: {"match": {"username": "..."}}
     */
    @Select("{\"query\":{\"term\":{\"username\":\"#{username}\"}}}")
    List<User> findByUsername(@Param("username") String username);

    /**
     * Find users within an age range (annotation-driven).
     */
    @Select("{\"query\":{\"range\":{\"age\":{\"gte\":#{minAge},\"lte\":#{maxAge}}}}}")
    List<User> findByAgeRange(@Param("minAge") int minAge, @Param("maxAge") int maxAge);

    /**
     * Update user status by id (annotation-driven).
     */
    @Update("{\"script\":{\"source\":\"ctx._source.status = #{status}\"}}")
    io.github.timtools.elasticmapper.executor.ESResponse updateStatus(@Param("status") String status);

    /**
     * Delete users by status (annotation-driven).
     */
    @Delete("{\"query\":{\"term\":{\"status\":\"#{status}\"}}}")
    io.github.timtools.elasticmapper.executor.ESResponse deleteByStatus(@Param("status") String status);

    /**
     * Full-text search users by keyword, with optional filters (XML-driven).
     * Defined in es-mapper/UserMapper.xml as "searchUsers".
     */
    List<User> searchUsers(@Param("keyword") String keyword,
                           @Param("minAge") Integer minAge,
                           @Param("status") String status);

    /**
     * Count users by status (XML-driven, aggregation).
     * Defined in es-mapper/UserMapper.xml as "countByStatus".
     */
    AggR<StatusCounts> countByStatus();
}
