package com.lenilestari.aethersea

import com.lenilestari.aethersea.processor.InputParser
import org.junit.Assert.assertEquals
import org.junit.Test

class InputParserTest {

    private fun p(input: String) = InputParser.parse(input)

    // ── Contoh wajib dari spesifikasi ──────────────────────────────────────

    @Test fun `tomat 5000`() = p("tomat 5000").run {
        assertEquals("tomat", nama); assertEquals(1, qty); assertEquals(5000, harga)
    }

    @Test fun `tomat 50_000 titik ribuan`() = p("tomat 50.000").run {
        assertEquals("tomat", nama); assertEquals(1, qty); assertEquals(50000, harga)
    }

    @Test fun `tomat 2 50_000`() = p("tomat 2 50.000").run {
        assertEquals("tomat", nama); assertEquals(2, qty); assertEquals(50000, harga)
    }

    @Test fun `telur 12 2_500`() = p("telur 12 2.500").run {
        assertEquals("telur", nama); assertEquals(12, qty); assertEquals(2500, harga)
    }

    // ── Kata angka + multiplier (via TextNormalizer) ───────────────────────

    @Test fun `kata angka - dua belas sebagai qty`() = p("telur dua belas 2.500").run {
        assertEquals("telur", nama); assertEquals(12, qty); assertEquals(2500, harga)
    }

    @Test fun `multiplier ribu`() = p("gula 5 ribu").run {
        assertEquals("gula", nama); assertEquals(1, qty); assertEquals(5000, harga)
    }

    @Test fun `multiplier juta`() = p("hp 2 juta").run {
        assertEquals("hp", nama); assertEquals(1, qty); assertEquals(2000000, harga)
    }

    @Test fun `ratus - lima ratus`() = p("kue 5 lima ratus").run {
        assertEquals("kue", nama); assertEquals(5, qty); assertEquals(500, harga)
    }

    // ── Noise / filler words (voice artifact) ─────────────────────────────

    @Test fun `noise - eh beli tomat lima ribu ya`() = p("eh beli tomat lima ribu ya").run {
        assertEquals("tomat", nama); assertEquals(1, qty); assertEquals(5000, harga)
    }

    @Test fun `noise - yang itu`() = p("yang itu telur 5000").run {
        assertEquals("telur", nama); assertEquals(1, qty); assertEquals(5000, harga)
    }

    // ── Nama kosong → "item" ───────────────────────────────────────────────

    @Test fun `tanpa nama - hanya harga`() = p("5000").run {
        assertEquals("item", nama); assertEquals(1, qty); assertEquals(5000, harga)
    }

    @Test fun `tanpa nama - qty dan harga`() = p("2 50.000").run {
        assertEquals("item", nama); assertEquals(2, qty); assertEquals(50000, harga)
    }

    // ── Voice digit sequence ───────────────────────────────────────────────

    @Test fun `digit tunggal berurutan - lima nol nol nol`() = p("susu lima nol nol nol").run {
        assertEquals("susu", nama); assertEquals(1, qty); assertEquals(5000, harga)
    }

    // ── Format angka Indonesia ─────────────────────────────────────────────

    @Test fun `koma sebagai pemisah ribuan`() = p("gula 2,500").run {
        assertEquals("gula", nama); assertEquals(1, qty); assertEquals(2500, harga)
    }

    @Test fun `jutaan dengan titik`() = p("daging 1.500.000").run {
        assertEquals("daging", nama); assertEquals(1, qty); assertEquals(1500000, harga)
    }

    // ── Nama multi-kata ────────────────────────────────────────────────────

    @Test fun `nama dua kata`() = p("mie goreng 3 15.000").run {
        assertEquals("mie goreng", nama); assertEquals(3, qty); assertEquals(15000, harga)
    }

    // ── Validasi batas ─────────────────────────────────────────────────────

    @Test fun `qty minimal 1`() = p("sabun 0 5000").run {
        assertEquals(1, qty)
    }

    @Test fun `tanpa angka sama sekali`() = p("sabun").run {
        assertEquals("sabun", nama); assertEquals(1, qty); assertEquals(0, harga)
    }
}
