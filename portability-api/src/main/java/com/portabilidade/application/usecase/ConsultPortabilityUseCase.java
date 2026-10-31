package com.portabilidade.application.usecase;

import com.portabilidade.service.PhoneNormalizer;
import com.portabilidade.service.PortabilityResolutionService;
import com.portabilidade.service.ProcessResult;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Use case síncrono: consulta 1..N números e resolve operadora/portadora.
 *
 * Hexagonal: depende apenas do serviço do core (que por sua vez só conhece os
 * ports PortabilityRepository/PrefixResolver). Sem conhecimento de HTTP/DTOs —
 * o mapper fica em infrastructure/web.
 */
@Service
public class ConsultPortabilityUseCase {

    /** Limite duro de números por request síncrona (mitigação de payload abusivo). */
    public static final int MAX_SYNC_NUMBERS = 100;

    private final PortabilityResolutionService resolutionService;
    private final PhoneNormalizer normalizer;

    public ConsultPortabilityUseCase(PortabilityResolutionService resolutionService,
                                     PhoneNormalizer normalizer) {
        this.resolutionService = resolutionService;
        this.normalizer = normalizer;
    }

    /**
     * @param rawNumbers números brutos (1..MAX_SYNC_NUMBERS)
     * @return resultado posicional do core (raw/operators/sources na mesma ordem)
     * @throws IllegalArgumentException se vazio ou acima do limite
     */
    public ProcessResult consult(List<String> rawNumbers) {
        if (rawNumbers == null || rawNumbers.isEmpty()) {
            throw new IllegalArgumentException("lista de numeros vazia");
        }
        if (rawNumbers.size() > MAX_SYNC_NUMBERS) {
            throw new IllegalArgumentException(
                    "maximo de " + MAX_SYNC_NUMBERS + " numeros por consulta sincrona; use o endpoint de batch");
        }
        return resolutionService.processNumbers(rawNumbers);
    }

    /** Normalização canônica (10/11 dígitos) exposta para o mapper de resposta. */
    public String canonical(String raw) {
        return normalizer.getCleanDigits(raw);
    }
}
