package com.aceballos.the_office_back.util;

import com.aceballos.the_office_back.exception.BusinessRuleException;
import com.aceballos.the_office_back.service.BrandService;
import com.aceballos.the_office_back.service.CategoryService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SlugUtilsTest {

    @Test
    void normalize_ShouldStripAccentsLowercaseAndCollapseSeparators() {
        assertEquals("zapatillas-nino-2024", SlugUtils.normalize("  Zapatíllas  Niño!! 2024 "));
        assertEquals("nike", SlugUtils.normalize("--Nike--"));
    }

    @Test
    void normalize_ShouldRejectBlankSymbolsOnlyAndTooLong() {
        assertThrows(BusinessRuleException.class, () -> SlugUtils.normalize(null));
        assertThrows(BusinessRuleException.class, () -> SlugUtils.normalize("   "));
        assertThrows(BusinessRuleException.class, () -> SlugUtils.normalize("!!!"));
        assertThrows(BusinessRuleException.class, () -> SlugUtils.normalize("a".repeat(101)));
        assertEquals(100, SlugUtils.normalize("a".repeat(100)).length());
    }

    @Test
    void serviceDelegates_ShouldKeepSameBehavior() {
        assertEquals("camperas-hombre", BrandService.normalizeSlug("Camperas Hombre"));
        assertEquals("camperas-hombre", CategoryService.normalizeSlug("Camperas Hombre"));
    }
}
