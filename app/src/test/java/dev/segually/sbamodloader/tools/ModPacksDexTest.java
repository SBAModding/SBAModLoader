package dev.segually.sbamodloader.tools;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class ModPacksDexTest {
    @Test
    public void numbersRootDexFiles() {
        assertEquals(1, ModPacks.dexNumber("classes.dex"));
        assertEquals(2, ModPacks.dexNumber("classes2.dex"));
        assertEquals(12, ModPacks.dexNumber("classes12.dex"));
    }

    @Test
    public void ignoresEverythingElse() {
        assertEquals(-1, ModPacks.dexNumber("classes1.dex"));
        assertEquals(-1, ModPacks.dexNumber("classes02.dex"));
        assertEquals(-1, ModPacks.dexNumber("classesx.dex"));
        assertEquals(-1, ModPacks.dexNumber("classes2.dex.bak"));
        assertEquals(-1, ModPacks.dexNumber("sub/classes2.dex"));
        assertEquals(-1, ModPacks.dexNumber("classes99999.dex"));
    }

    @Test
    public void namesRoundTrip() {
        assertEquals("classes.dex", ModPacks.dexName(1));
        assertEquals("classes5.dex", ModPacks.dexName(5));
        assertEquals(5, ModPacks.dexNumber(ModPacks.dexName(5)));
    }
}
