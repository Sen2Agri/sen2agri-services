package org.esa.sen2agri.entities;

import org.esa.sen2agri.commons.Constants;

import java.util.HashMap;
import java.util.Map;

/**
 * Helper class holding the different values for the Sentinel-2 product types.
 * It is not nice, but different providers use different labels for the same produc type.
 *
 * @author Cosmin Cara
 */
public abstract class ProductTypes {
    public static final ProductTypes Sentinel2 = new Sentinel2();

    protected final Map<String, Map<String, String>> mappings;
    private ProductTypes() {
        this.mappings = new HashMap<>();
    }

    public abstract String getValue(String productType, String dataSource);

    private static class Sentinel2 extends ProductTypes {

        private Sentinel2() {
            super();
            this.mappings.put("Scientific Data Hub",
                              new HashMap<String, String>() {{
                                  put(Constants.S2L1C_PRODUCT_TYPE, "S2MSI1C");
                                  put(Constants.S2L2A_PRODUCT_TYPE, "S2MSI2A");
                              }});
            this.mappings.put("Creo DIAS",
                    new HashMap<String, String>() {{
                        put(Constants.S2L1C_PRODUCT_TYPE, "L1C");
                        put(Constants.S2L2A_PRODUCT_TYPE, "L2A");
                    }});
            this.mappings.put("Mundi DIAS",
                    new HashMap<String, String>() {{
                        put(Constants.S2L1C_PRODUCT_TYPE, "S2MSI1C");
                        put(Constants.S2L2A_PRODUCT_TYPE, "S2MSI2A");
                    }});
        }

        @Override
        public String getValue(String productType, String dataSource) {
            String value = null;
            final Map<String, String> map = this.mappings.get(dataSource);
            if (map != null) {
                value = map.get(productType);
            }
            return value;
        }
    }
}
