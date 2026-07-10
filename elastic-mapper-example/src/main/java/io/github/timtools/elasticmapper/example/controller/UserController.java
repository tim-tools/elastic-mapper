package io.github.timtools.elasticmapper.example.controller;

import io.github.timtools.elasticmapper.example.dto.StatusCounts;
import io.github.timtools.elasticmapper.example.entity.User;
import io.github.timtools.elasticmapper.example.service.UserService;
import io.github.timtools.elasticmapper.executor.ESResponse;
import io.github.timtools.elasticmapper.result.AggR;

import java.util.Map;
import io.github.timtools.elasticmapper.result.Page;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller exposing User operations via HTTP.
 * Demonstrates the full ElasticMapper usage stack in a Spring Boot application.
 */
@RestController
@RequestMapping("/api/users")
public class UserController {

    @Autowired
    private UserService userService;

    // ── CRUD ──

    @PostMapping
    public ESResponse create(@RequestBody User user) {
        return userService.createUser(user);
    }

    @GetMapping("/{id}")
    public User getById(@PathVariable String id) {
        return userService.getUserById(id);
    }

    @GetMapping
    public List<User> listAll() {
        return userService.listAllUsers();
    }

    @GetMapping("/paged")
    public Page<User> listPaged(@RequestParam(defaultValue = "1") int page,
                                @RequestParam(defaultValue = "10") int size) {
        return userService.listUsersPaged(page, size);
    }

    @PutMapping("/{id}")
    public ESResponse update(@PathVariable String id, @RequestBody User user) {
        user.setId(id);
        return userService.updateUser(user);
    }

    @DeleteMapping("/{id}")
    public ESResponse delete(@PathVariable String id) {
        return userService.deleteUser(id);
    }

    // ── Custom queries ──

    @GetMapping("/search")
    public List<User> search(@RequestParam(required = false) String keyword,
                             @RequestParam(required = false) Integer minAge,
                             @RequestParam(required = false) String status) {
        return userService.searchUsers(keyword, minAge, status);
    }

    @GetMapping("/by-username/{username}")
    public List<User> getByUsername(@PathVariable String username) {
        return userService.findByUsername(username);
    }

    @GetMapping("/by-age")
    public List<User> getByAgeRange(@RequestParam int minAge, @RequestParam int maxAge) {
        return userService.findByAgeRange(minAge, maxAge);
    }

    @GetMapping("/count-by-status")
    public StatusCounts countByStatus() {
        return userService.countByStatus().getAggregations();
    }

    @DeleteMapping("/by-status/{status}")
    public ESResponse deleteByStatus(@PathVariable String status) {
        return userService.deleteByStatus(status);
    }
}
