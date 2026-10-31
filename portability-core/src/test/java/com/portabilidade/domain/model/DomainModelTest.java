package com.portabilidade.domain.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Modelos de domínio")
class DomainModelTest {

    @Test
    @DisplayName("PrefixMapping desserializa do formato do prefixos_operadora.json")
    void prefixMappingJackson() throws Exception {
        String json = """
                { "operadora": "CLARO", "rn1": "55321", "qtd": 103, "desconhecido": true }
                """;
        PrefixMapping m = new ObjectMapper().readValue(json, PrefixMapping.class);
        assertEquals("CLARO", m.getOperadora());
        assertEquals("55321", m.getRn1());
        assertEquals(103, m.getQtd());

        m.setOperadora("TIM");
        m.setRn1("999");
        m.setQtd(1);
        assertEquals("TIM", m.getOperadora());
        assertEquals("999", m.getRn1());
        assertEquals(1, m.getQtd());
    }

    @Test
    @DisplayName("OperatorInfo record expoe rn1 e name")
    void operatorInfoRecord() {
        OperatorInfo op = new OperatorInfo("55321", "CLARO");
        assertEquals("55321", op.rn1());
        assertEquals("CLARO", op.name());
        assertEquals(op, new OperatorInfo("55321", "CLARO"));
    }

    @Test
    @DisplayName("PhoneNumber isValid apenas quando normalizado != null")
    void phoneNumberValidity() {
        PhoneNumber valid = new PhoneNumber("(11) 98765-4321", 11987654321L);
        PhoneNumber invalid = new PhoneNumber("123", null);
        assertTrue(valid.isValid());
        assertFalse(invalid.isValid());
        assertEquals(11987654321L, valid.normalized());
    }
}
