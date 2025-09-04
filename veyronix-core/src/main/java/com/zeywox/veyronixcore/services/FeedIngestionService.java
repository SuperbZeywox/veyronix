package com.zeywox.veyronixcore.services;

import com.zeywox.veyronixcore.dto.ProductIn;
import com.zeywox.veyronixcore.models.Product;
import com.zeywox.veyronixcore.repos.ProductRepository;
import com.zeywox.veyronixcore.util.IdRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class FeedIngestionService {
    private static final Logger log = LoggerFactory.getLogger(FeedIngestionService.class);

    private final IdRegistry ids;
    private final ProductRepository repo;

    public FeedIngestionService(IdRegistry ids, ProductRepository repo) {
        this.ids = ids;
        this.repo = repo;
    }


    public List<Product> ingest(List<ProductIn> incoming) {
        if (incoming == null || incoming.isEmpty()) return List.of();

        List<Product> accepted = new ArrayList<>(incoming.size());
        int rejected = 0;

        for (ProductIn in : incoming) {
            try {
                if (in.name() == null || in.name().isEmpty()) {
                    log.warn("Ingest skip: missing/blank name: {}", in);
                    rejected++; continue;
                }
                String id = ids.lookupOrCreateId(in.name(), in.category());
                Product p = new Product(id, in.name(), in.category(), in.price(), in.description(), in.stock());
                repo.upsert(p);
                accepted.add(p);
            } catch (Exception e) {
                log.warn("Ingest skip: unexpected error on row {} -> {}", in, e.toString());
                rejected++;
            }
        }
        log.info("Ingest summary: accepted={}, rejected={}", accepted.size(), rejected);
        return accepted;
    }

}

