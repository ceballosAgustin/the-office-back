package com.aceballos.the_office_back.config;

import com.aceballos.the_office_back.entity.Category;
import com.aceballos.the_office_back.entity.NavItem;
import com.aceballos.the_office_back.entity.NavSection;
import com.aceballos.the_office_back.entity.NavTargetType;
import com.aceballos.the_office_back.repository.CategoryRepository;
import com.aceballos.the_office_back.repository.NavSectionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class NavigationDataInitializer implements ApplicationRunner {

    private final NavSectionRepository navSectionRepository;
    private final CategoryRepository categoryRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (navSectionRepository.count() > 0) {
            log.info("NavigationDataInitializer: Configuración de navegación existente encontrada. Omitiendo inicialización.");
            return;
        }

        log.info("NavigationDataInitializer: Inicializando secciones por defecto del navbar...");

        // 1. Brands (sin marcas precargadas)
        NavSection brandsSection = NavSection.builder()
                .code("brands")
                .label("Marcas")
                .orderIndex(0)
                .isVisible(true)
                .items(new ArrayList<>())
                .build();
        navSectionRepository.save(brandsSection);

        // 2. Sneakers
        NavSection sneakersSection = NavSection.builder()
                .code("sneakers")
                .label("Sneakers")
                .orderIndex(1)
                .isVisible(true)
                .items(new ArrayList<>())
                .build();

        findCategoryByPossibleSlugs("sneakers", "calzado", "zapatillas").ifPresent(cat -> {
            NavItem item = NavItem.builder()
                    .section(sneakersSection)
                    .targetType(NavTargetType.CATEGORY)
                    .category(cat)
                    .columnIndex(0)
                    .orderIndex(0)
                    .isVisible(true)
                    .build();
            sneakersSection.addItem(item);
        });
        navSectionRepository.save(sneakersSection);

        // 3. Streetwear
        NavSection streetwearSection = NavSection.builder()
                .code("streetwear")
                .label("Streetwear")
                .orderIndex(2)
                .isVisible(true)
                .items(new ArrayList<>())
                .build();

        Optional<Category> topsCat = findCategoryByPossibleSlugs("tops", "remeras");
        Optional<Category> bottomsCat = findCategoryByPossibleSlugs("bottoms", "pantalones");

        int streetwearOrder = 0;
        if (topsCat.isPresent()) {
            NavItem topsItem = NavItem.builder()
                    .section(streetwearSection)
                    .targetType(NavTargetType.CATEGORY)
                    .category(topsCat.get())
                    .columnIndex(0)
                    .orderIndex(streetwearOrder++)
                    .isVisible(true)
                    .build();
            streetwearSection.addItem(topsItem);
        }
        if (bottomsCat.isPresent()) {
            NavItem bottomsItem = NavItem.builder()
                    .section(streetwearSection)
                    .targetType(NavTargetType.CATEGORY)
                    .category(bottomsCat.get())
                    .columnIndex(0)
                    .orderIndex(streetwearOrder++)
                    .isVisible(true)
                    .build();
            streetwearSection.addItem(bottomsItem);
        }

        if (topsCat.isEmpty() && bottomsCat.isEmpty()) {
            findCategoryByPossibleSlugs("streetwear", "indumentaria").ifPresent(cat -> {
                NavItem item = NavItem.builder()
                        .section(streetwearSection)
                        .targetType(NavTargetType.CATEGORY)
                        .category(cat)
                        .columnIndex(0)
                        .orderIndex(0)
                        .isVisible(true)
                        .build();
                streetwearSection.addItem(item);
            });
        }
        navSectionRepository.save(streetwearSection);

        // 4. Accessories
        NavSection accessoriesSection = NavSection.builder()
                .code("accessories")
                .label("Accesorios")
                .orderIndex(3)
                .isVisible(true)
                .items(new ArrayList<>())
                .build();

        findCategoryByPossibleSlugs("accessories", "accesorios").ifPresent(cat -> {
            NavItem item = NavItem.builder()
                    .section(accessoriesSection)
                    .targetType(NavTargetType.CATEGORY)
                    .category(cat)
                    .columnIndex(0)
                    .orderIndex(0)
                    .isVisible(true)
                    .build();
            accessoriesSection.addItem(item);
        });
        navSectionRepository.save(accessoriesSection);

        log.info("NavigationDataInitializer: Secciones por defecto inicializadas correctamente (brands, sneakers, streetwear, accessories).");
    }

    private Optional<Category> findCategoryByPossibleSlugs(String... slugs) {
        for (String slug : slugs) {
            Optional<Category> cat = categoryRepository.findBySlug(slug);
            if (cat.isPresent()) {
                return cat;
            }
        }
        return Optional.empty();
    }
}

