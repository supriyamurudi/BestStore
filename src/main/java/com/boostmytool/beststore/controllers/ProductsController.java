package com.boostmytool.beststore.controllers;

import java.io.InputStream;
import java.nio.file.*;
import java.util.Date;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import com.boostmytool.beststore.models.Product;
import com.boostmytool.beststore.models.ProductDto;
import com.boostmytool.beststore.services.ProductsRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;

@Controller
@RequestMapping("/products")
public class ProductsController {

    @Autowired
    private ProductsRepository repo;

    @PersistenceContext
    private EntityManager entityManager;

    private final String UPLOAD_DIR = "public/images/";

    /* ================= LIST ================= */
    @GetMapping({"", "/"})
    public String showProductList(Model model) {
        model.addAttribute("products", repo.findAll(Sort.by(Sort.Direction.DESC, "id")));
        return "products/index";
    }

    /* ================= CREATE ================= */
    @GetMapping("/create")
    public String showCreatePage(Model model) {
        model.addAttribute("productDto", new ProductDto());
        return "products/CreateProduct";
    }

    @PostMapping("/create")
    public String createProduct(
            @Valid @ModelAttribute("productDto") ProductDto productDto,
            BindingResult result
    ) {

        if (productDto.getImageFile().isEmpty()) {
            result.addError(new FieldError("productDto", "imageFile", "Image is required"));
        }

        if (result.hasErrors()) {
            return "products/CreateProduct";
        }

        String fileName = saveImage(productDto.getImageFile());

        Product product = new Product();
        product.setName(productDto.getName());
        product.setBrand(productDto.getBrand());
        product.setCategory(productDto.getCategory());
        product.setPrice(productDto.getPrice());
        product.setDescription(productDto.getDescription());
        product.setCreatedAt(new Date());
        product.setImageFileName(fileName);

        repo.save(product);
        return "redirect:/products";
    }

    /* ================= EDIT ================= */
    @GetMapping("/edit")
    public String showEditPage(@RequestParam("id") int id, Model model) {
        Product product = repo.findById(id)
                .orElseThrow(() -> new RuntimeException("Product not found"));

        ProductDto dto = new ProductDto();
        dto.setName(product.getName());
        dto.setBrand(product.getBrand());
        dto.setCategory(product.getCategory());
        dto.setPrice(product.getPrice());
        dto.setDescription(product.getDescription());

        model.addAttribute("product", product);
        model.addAttribute("productDto", dto);
        return "products/EditProduct";
    }

    @PostMapping("/edit")
    public String updateProduct(
            @RequestParam int id,
            @Valid @ModelAttribute("productDto") ProductDto dto,
            BindingResult result,
            Model model
    ) {
        Product product = repo.findById(id)
                .orElseThrow(() -> new RuntimeException("Product not found"));

        model.addAttribute("product", product);

        if (result.hasErrors()) {
            return "products/EditProduct";
        }

        if (!dto.getImageFile().isEmpty()) {
            deleteImage(product.getImageFileName());
            String newFileName = saveImage(dto.getImageFile());
            product.setImageFileName(newFileName);
        }

        product.setName(dto.getName());
        product.setBrand(dto.getBrand());
        product.setCategory(dto.getCategory());
        product.setPrice(dto.getPrice());
        product.setDescription(dto.getDescription());

        repo.save(product);
        return "redirect:/products";
    }

    /* ================= DELETE ================= */
    @GetMapping("/delete")
    @Transactional
    public String deleteProduct(@RequestParam("id") int id) {
        Product product = repo.findById(id)
                .orElseThrow(() -> new RuntimeException("Product not found"));

        deleteImage(product.getImageFileName());
        repo.delete(product);

        // Only renumber if using auto_increment workaround
        renumberProductIds();

        return "redirect:/products";
    }

    /* ================= MANAGE PRODUCTS ================= */
    @GetMapping("/manage")
    public String manageProducts(Model model) {
        model.addAttribute("products", repo.findAll(Sort.by(Sort.Direction.ASC, "id")));
        return "products/ManageProducts";
    }

    /* ================= HELPERS ================= */
    private String saveImage(MultipartFile image) {
        try {
            Files.createDirectories(Paths.get(UPLOAD_DIR));
            String fileName = System.currentTimeMillis() + "_" + image.getOriginalFilename();
            try (InputStream in = image.getInputStream()) {
                Files.copy(in, Paths.get(UPLOAD_DIR + fileName), StandardCopyOption.REPLACE_EXISTING);
            }
            return fileName;
        } catch (Exception e) {
            throw new RuntimeException("Image save failed", e);
        }
    }

    private void deleteImage(String fileName) {
        if (fileName == null) return;
        try {
            Files.deleteIfExists(Paths.get(UPLOAD_DIR + fileName));
        } catch (Exception ignored) {}
    }

    @Transactional
    private void renumberProductIds() {
        List<Product> products = repo.findAll(Sort.by(Sort.Direction.ASC, "id"));
        int count = 1;
        for (Product p : products) {
            if (p.getId() != count) {
                entityManager.createNativeQuery("UPDATE products SET id = :newId WHERE id = :oldId")
                        .setParameter("newId", count)
                        .setParameter("oldId", p.getId())
                        .executeUpdate();
            }
            count++;
        }
        entityManager.createNativeQuery("ALTER TABLE products AUTO_INCREMENT = :nextId")
                .setParameter("nextId", count)
                .executeUpdate();
    }
}
