package com.obraslink.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CampoOrcamentoCatalogoTest {
    @Test
    void categoriasComCatalogoTemCamposEChavesUnicas() {
        for (String categoria : List.of("Pintor", "Eletricista", "Mestre de Obras")) {
            var campos = CampoOrcamentoCatalogo.campos(categoria);
            assertFalse(campos.isEmpty(), categoria);
            assertEquals(campos.size(), campos.stream().map(CampoOrcamentoCatalogo.Campo::chave).distinct().count(), categoria);
        }
    }

    @Test
    void categoriaSemCatalogoSoUsaCamposGerais() {
        assertTrue(CampoOrcamentoCatalogo.campos("Encanador").isEmpty());
        assertTrue(CampoOrcamentoCatalogo.campos("Categoria Inexistente").isEmpty());
    }
}
