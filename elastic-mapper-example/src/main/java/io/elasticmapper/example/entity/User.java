package io.elasticmapper.example.entity;

import io.elasticmapper.annotations.Field;
import io.elasticmapper.annotations.Id;
import io.elasticmapper.annotations.IndexName;

import java.time.LocalDateTime;

/**
 * Example entity mapped to the "users" Elasticsearch index.
 */
@IndexName("users")
public class User {

    @Id
    private String id;

    /** Mapped as keyword for exact matching. */
    @Field(type = "keyword")
    private String username;

    /** Mapped as text for full-text search. */
    @Field(type = "text", analyzer = "standard")
    private String bio;

    @Field(type = "keyword")
    private String email;

    @Field(type = "integer")
    private Integer age;

    /** Stored as keyword for enum-style exact match. */
    @Field(type = "keyword")
    private String status;

    @Field(type = "date", format = "yyyy-MM-dd HH:mm:ss")
    private String createdAt;

    // ── Constructors ──

    public User() {}

    public User(String id, String username, String email, Integer age, String status) {
        this.id = id;
        this.username = username;
        this.email = email;
        this.age = age;
        this.status = status;
        this.createdAt = java.time.LocalDateTime.now().toString().replace('T', ' ');
    }

    // ── Getters & Setters ──

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getBio() { return bio; }
    public void setBio(String bio) { this.bio = bio; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public Integer getAge() { return age; }
    public void setAge(Integer age) { this.age = age; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    @Override
    public String toString() {
        return "User{id='" + id + "', username='" + username + "', email='" + email +
                "', age=" + age + ", status='" + status + "'}";
    }
}
