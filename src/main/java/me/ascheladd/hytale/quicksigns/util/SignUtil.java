package me.ascheladd.hytale.quicksigns.util;

import com.hypixel.hytale.server.core.asset.type.item.config.Item;

/**
 * Utility class for sign-related operations.
 */
public class SignUtil {
    
    /**
     * Private constructor to prevent instantiation.
     */
    private SignUtil() {
        throw new UnsupportedOperationException("Utility class");
    }
    
    /**
     * Category identifier for furniture signs in Hytale.
     */
    private static final String SIGN_CATEGORY = "Furniture.Signs";
    
    /**
     * Item ID for the construction sign (not editable).
     */
    private static final String CONSTRUCTION_SIGN_ID = "Furniture_Construction_Sign";
    private static final String LUMBERJACK_SIGN_ID = "Furniture_Lumberjack_Sign";
    
    /**
     * Checks if an Item is an editable sign.
     * Checks if the item has the "Furniture.Signs" category.
     * Excludes Furniture_Construction_Sign.
     * 
     * @param item The item to check
     * @return true if the item is an editable sign, false otherwise
     */
    public static boolean isEditableSign(Item item) {
        if (item == null) {
            return false;
        }
        
        // Exclude construction sign
        String itemId = item.getId();
        if (itemId == null) {
            return false;
        }

        if (itemId.equals(CONSTRUCTION_SIGN_ID) || itemId.equals(LUMBERJACK_SIGN_ID)) {
            return false;
        }
        
        String[] categories = item.getCategories();
        if (categories == null) {
            return false;
        }
        
        for (String category : categories) {
            if (SIGN_CATEGORY.equals(category)) {
                return true;
            }
        }
        
        return false;
    }
}
