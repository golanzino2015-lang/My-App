package com.questionbubble.app;

/** The color themes. Each is an accent (with a lighter and darker shade for metallic gradients). */
final class Palette {

    final String id, name;
    final int base, light, dark;

    private Palette(String id, String name, int base, int light, int dark) {
        this.id = id;
        this.name = name;
        this.base = base;
        this.light = light;
        this.dark = dark;
    }

    boolean isClassic() {
        return "classic".equals(id);
    }

    static final Palette[] ALL = {
            new Palette("classic", "Classic", 0xFFFFFFFF, 0xFFFFFFFF, 0xFFD6D6D6),
            new Palette("silver", "Silver", 0xFFC9CED6, 0xFFF4F6F9, 0xFF8D939C),
            new Palette("platinum", "Platinum", 0xFFE8E6E1, 0xFFFFFFFF, 0xFFB3B0AA),
            new Palette("gold", "Gold", 0xFFE2B93B, 0xFFF8E39A, 0xFFA67C1A),
            new Palette("rose_gold", "Rose gold", 0xFFE8A49B, 0xFFF7D2CB, 0xFFB26F68),
            new Palette("bronze", "Bronze", 0xFFD08A4E, 0xFFECB98A, 0xFF8F5527),
            new Palette("copper", "Copper", 0xFFDA8A55, 0xFFF2B990, 0xFF9B5428),
            new Palette("champagne", "Champagne", 0xFFF1DDB5, 0xFFFFF4DC, 0xFFC4A876),
            new Palette("emerald", "Emerald", 0xFF4ADE80, 0xFFA7F3C4, 0xFF1F9D55),
            new Palette("mint", "Mint", 0xFF7EE8C3, 0xFFC2F7E4, 0xFF3BAF8A),
            new Palette("sapphire", "Sapphire", 0xFF6EA8FF, 0xFFB5D2FF, 0xFF3366CC),
            new Palette("ocean", "Ocean", 0xFF4DD4E6, 0xFFA5EEF7, 0xFF1E95A8),
            new Palette("amethyst", "Amethyst", 0xFFB98CFF, 0xFFDCC6FF, 0xFF7A4FC9),
            new Palette("ruby", "Ruby", 0xFFFF5C8A, 0xFFFFADC5, 0xFFC2244F),
            new Palette("sunset", "Sunset", 0xFFFF9A57, 0xFFFFC9A3, 0xFFD2621E),
            new Palette("pink", "Pink", 0xFFFF8AD0, 0xFFFFC4E8, 0xFFD14E9C),
    };

    static Palette get(String id) {
        for (Palette p : ALL) if (p.id.equals(id)) return p;
        return ALL[0];
    }
}
