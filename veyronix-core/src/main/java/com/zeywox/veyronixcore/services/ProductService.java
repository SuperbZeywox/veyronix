package com.zeywox.veyronixcore.services;

import com.zeywox.veyronixcore.dto.PatchProductRequest;
import com.zeywox.veyronixcore.models.Product;
import com.zeywox.veyronixcore.repos.ProductRepository;
import com.zeywox.veyronixcore.util.IdRegistry;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;

@Service
public class ProductService {

    private final ProductRepository repo;
    private final IdRegistry idRegistry; // only for NK remap on name/category change

    public ProductService(ProductRepository repo, IdRegistry idRegistry) {
        this.repo = repo;
        this.idRegistry = idRegistry;
    }

    // ---- Reads ----
    public Optional<Product> getOne(String id) {
        return repo.getOne(id);
    }

    public List<Product> listByCategory(String category, Optional<Boolean> inStockFilter, int page, int size) {
        if (category == null || category.isBlank()) {
            throw new IllegalArgumentException("category is required and cannot be blank");
        }
        var ids = repo.listIdsByCategory(category, inStockFilter, page, size);
        if (ids.isEmpty()) return List.of();
        return repo.getMany(ids);
    }

    // ---- Mutations ----
    public Product patch(String id, PatchProductRequest req) {
        Product old = repo.getOne(id)
                .orElseThrow(() -> new NoSuchElementException("Product not found: " + id));

        String  name        = old.name();
        String  category    = old.category();
        Double  price       = old.price();
        String  description = old.description();
        Integer stock       = old.stock();

        boolean nameChange = false, categoryChange = false, anyChange = false;

        // name
        if (req.name != null) {
            if (req.name.isEmpty()) throw new IllegalArgumentException("name cannot be blank");
            if (!Objects.equals(req.name, name)) { name = req.name; nameChange = anyChange = true; }
        }

        // category (empty => "uncategorized")
        if (req.category != null) {
            String c = req.category.isEmpty() ? "uncategorized" : req.category;
            if (!Objects.equals(c, category)) { category = c; categoryChange = anyChange = true; }
        }

        // price (already scaled/validated by DoublePriceDeserializer)
        if (req.price != null && !Objects.equals(req.price, price)) {
            price = req.price; anyChange = true;
        }

        // description
        if (req.description != null && !Objects.equals(req.description, description)) {
            description = req.description; anyChange = true;
        }

        // stock (@PositiveOrZero already validated)
        if (req.stock != null && !Objects.equals(req.stock, stock)) {
            stock = req.stock; anyChange = true;
        }

        // product cannot own previous construction steps because of this line
        // i can create Optional<Product> , but i don't want to add overhead
        if (!anyChange) return old;

        Product updated = new Product(id, name, category, price, description, stock);
        repo.upsert(updated);

        // internal flag if you want it
//        req.keyChange = nameChange || categoryChange;

        if (nameChange || categoryChange) {
            idRegistry.remapIfChanged(old.name(), old.category(), name, category, id);
        }

        return updated;
    }



    public int setStock(String id, int stock) {
        if (stock < 0) throw new IllegalArgumentException("stock must be >= 0");
        try {
            return repo.setStock(id, stock);
        } catch (EmptyResultDataAccessException notFound) {
            throw new NoSuchElementException("Product not found: " + id);
        }
    }
}




