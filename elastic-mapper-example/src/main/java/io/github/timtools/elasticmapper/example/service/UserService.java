package io.github.timtools.elasticmapper.example.service;

import io.github.timtools.elasticmapper.example.dto.StatusCounts;
import io.github.timtools.elasticmapper.example.entity.User;
import io.github.timtools.elasticmapper.example.mapper.UserMapper;
import io.github.timtools.elasticmapper.executor.ESResponse;
import io.github.timtools.elasticmapper.result.AggR;
import io.github.timtools.elasticmapper.result.Page;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Service layer demonstrating common ElasticMapper usage patterns:
 * insert, select, update, delete, pagination, and XML-driven queries.
 */
@Service
public class UserService {

    @Autowired
    private UserMapper userMapper;

    // ── Built-in CRUD ──

    /** Insert a single user. */
    public ESResponse createUser(User user) {
        return userMapper.insert(user);
    }

    /** Find user by ID. */
    public User getUserById(String id) {
        return userMapper.selectById(id);
    }

    /** List all users (capped at maxResultWindow). */
    public List<User> listAllUsers() {
        return userMapper.selectList();
    }

    /** Paginated listing with from/size. */
    public Page<User> listUsersPaged(int page, int size) {
        Page<User> pageReq = new Page<>(page, size);
        return userMapper.selectPage(pageReq);
    }

    /** Full update by ID. */
    public ESResponse updateUser(User user) {
        return userMapper.updateById(user);
    }

    /** Delete by ID. */
    public ESResponse deleteUser(String id) {
        return userMapper.deleteById(id);
    }

    // ── Annotation-driven custom queries ──

    /** Find users by exact username. */
    public List<User> findByUsername(String username) {
        return userMapper.findByUsername(username);
    }

    /** Find users within an age range. */
    public List<User> findByAgeRange(int minAge, int maxAge) {
        return userMapper.findByAgeRange(minAge, maxAge);
    }

    /** Delete all users with the given status. */
    public ESResponse deleteByStatus(String status) {
        return userMapper.deleteByStatus(status);
    }

    // ── XML-driven queries ──

    /** Full-text search with optional filters (XML-driven). */
    public List<User> searchUsers(String keyword, Integer minAge, String status) {
        return userMapper.searchUsers(keyword, minAge, status);
    }

    /** Count users grouped by status via aggregation (XML-driven). */
    public AggR<StatusCounts> countByStatus() {
        return userMapper.countByStatus();
    }
}
