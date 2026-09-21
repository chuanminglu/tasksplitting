package com.tasksplitting.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/todos")
@CrossOrigin(origins = "http://localhost:5173")
public class TodoController {
    private final TodoRepository repository;

    public TodoController(TodoRepository repository) { this.repository = repository; }

    @GetMapping
    public List<Todo> findAll() { return repository.findAll(); }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody TodoRequest request) {
        String title = request == null || request.title() == null ? "" : request.title().trim();
        if (title.isEmpty()) return ResponseEntity.badRequest().body(Map.of("message", "title is required"));
        return ResponseEntity.status(HttpStatus.CREATED).body(repository.create(title));
    }
}
