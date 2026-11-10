package com.aceballos.the_office_back.util;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/**
 * Utilidad centralizada para formatear importes monetarios en dólares estadounidenses (USD)
 * dentro de los mensajes generados por el backend (ej. enlaces de WhatsApp).
 */
public final class CurrencyFormatter {

    private static final String CURRENCY_SUFFIX = " USD";

    private CurrencyFormatter() {
    }

    /**
     * Formatea un importe monetario en USD respetando los criterios de la tienda:
     * - Sin símbolo '$'.
     * - Sin referencia a ARS.
     * - Separador de miles con punto ('.').
     * - Separador decimal con coma (',').
     * - Sin decimales cuando el importe es entero (ej. 150 -> "150 USD", 1500 -> "1.500 USD").
     * - Dos decimales cuando el importe posee parte decimal (ej. 150.50 -> "150,50 USD", 150000.75 -> "150.000,75 USD").
     */
    public static String formatUsd(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) == 0) {
            return "0" + CURRENCY_SUFFIX;
        }

        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.forLanguageTag("es-AR"));
        symbols.setGroupingSeparator('.');
        symbols.setDecimalSeparator(',');

        BigDecimal stripped = amount.stripTrailingZeros();
        DecimalFormat df;
        if (stripped.scale() <= 0) {
            df = new DecimalFormat("#,##0", symbols);
        } else {
            df = new DecimalFormat("#,##0.00", symbols);
        }

        return df.format(amount) + CURRENCY_SUFFIX;
    }
}

