package com.aceballos.the_office_back.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "nav_sections")
public class NavSection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50, updatable = false)
    private String code;

    @Column(nullable = false, length = 100)
    private String label;

    @Column(name = "order_index", nullable = false)
    @Builder.Default
    private Integer orderIndex = 0;

    @Column(name = "is_visible", nullable = false)
    @Builder.Default
    private Boolean isVisible = true;

    @OneToMany(mappedBy = "section", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<NavItem> items = new ArrayList<>();

    public void addItem(NavItem item) {
        items.add(item);
        item.setSection(this);
    }

    public void removeItem(NavItem item) {
        items.remove(item);
        item.setSection(null);
    }
}

